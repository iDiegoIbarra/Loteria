package com.diegoibarra.loteria.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import com.diegoibarra.loteria.render.CardTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Carta en edición: imagen original copiada a src y su diseño. */
@Serializable
data class CardDraft(
    val id: String,
    val source: String,
    val design: CardDesign,
    val existing: Boolean = false,
)

/**
 * Juegos (barajas) con sus cartas y tableros. Todo vive en filesDir:
 * games.json (índice) y games/<juego>/{src,render}/ (imágenes originales y cartas renderizadas).
 */
class GameRepository private constructor(context: Context) {

    private val app = context.applicationContext
    private val root = File(app.filesDir, "games").apply { mkdirs() }
    private val index = File(app.filesDir, "games.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun game(id: String): Game? = _games.value.firstOrNull { it.id == id }

    fun srcDir(gameId: String) = File(root, "$gameId/src").apply { mkdirs() }
    fun renderDir(gameId: String) = File(root, "$gameId/render").apply { mkdirs() }
    fun renderFile(gameId: String, card: CardSpec) = File(renderDir(gameId), card.render)
    fun sourceFile(gameId: String, name: String) = File(srcDir(gameId), name)

    suspend fun ensureLoaded() = mutex.withLock {
        if (_loaded.value) return@withLock
        val list = withContext(Dispatchers.IO) {
            val stored = runCatching {
                if (index.exists()) json.decodeFromString<List<Game>>(index.readText()) else null
            }.getOrNull()
            stored ?: migrateLegacy().also { write(it) }
        }
        _games.value = list
        _loaded.value = true
    }

    // ---------- Juegos ----------

    suspend fun createGame(name: String, boardCount: Int, boardSide: Int): Game = update { list ->
        val game = Game(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Nuevo juego" },
            boardCount = boardCount,
            boardSide = boardSide,
            createdAt = System.currentTimeMillis(),
        )
        (list + game) to game
    }

    suspend fun deleteGame(id: String) {
        update { list -> list.filterNot { it.id == id } to Unit }
        withContext(Dispatchers.IO) { File(root, id).deleteRecursively() }
    }

    /** Cambia nombre y configuración; si cambian tableros o tamaño, se rebarajan. */
    suspend fun saveSettings(id: String, name: String, boardCount: Int, boardSide: Int) = editGame(id) { g ->
        val changed = g.boardCount != boardCount || g.boardSide != boardSide
        val next = g.copy(name = name.trim().ifEmpty { g.name }, boardCount = boardCount, boardSide = boardSide)
        if (changed) withBoards(next) else next
    }

    // ---------- Cartas ----------

    /** Copia las imágenes elegidas al almacenamiento privado del juego; devuelve los nombres de archivo. */
    suspend fun importSources(gameId: String, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        uris.mapNotNull { uri ->
            val ext = resolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
            val name = "${UUID.randomUUID()}.$ext"
            val dst = File(srcDir(gameId), name)
            runCatching {
                resolver.openInputStream(uri)!!.use { input -> dst.outputStream().use { input.copyTo(it) } }
                check(dst.length() > 0)
                name
            }.getOrElse {
                dst.delete()
                null
            }
        }
    }

    fun discardSources(gameId: String, names: List<String>) {
        names.forEach { File(srcDir(gameId), it).delete() }
    }

    /** Guarda cartas nuevas o editadas. Si se añadieron cartas, los tableros se rebarajan. */
    suspend fun saveDrafts(gameId: String, drafts: List<CardDraft>) {
        val typeface = CardTemplate.typeface(app)
        val rendered = withContext(Dispatchers.IO) {
            drafts.associate { d ->
                val image = CardTemplate.decode(sourceFile(gameId, d.source), 2400)
                val bmp = CardTemplate.render(d.design, image, typeface)
                val version = (game(gameId)?.cards?.firstOrNull { it.id == d.id }?.version ?: 0) + 1
                val name = "${d.id}_v$version.jpg"
                File(renderDir(gameId), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle()
                image?.recycle()
                d.id to (name to version)
            }
        }
        val old = mutableListOf<String>()
        editGame(gameId) { g ->
            var added = false
            val now = System.currentTimeMillis()
            val cards = g.cards.map { c ->
                rendered[c.id]?.let { (name, version) ->
                    old += c.render
                    c.copy(render = name, version = version, design = drafts.first { it.id == c.id }.design)
                } ?: c
            }.toMutableList()
            drafts.filterNot { d -> g.cards.any { it.id == d.id } }.forEachIndexed { i, d ->
                val (name, version) = rendered.getValue(d.id)
                cards += CardSpec(d.id, CardKind.TEMPLATE, name, d.source, d.design, version, now + i)
                added = true
            }
            val next = g.copy(cards = cards)
            if (added) withBoards(next) else next
        }
        withContext(Dispatchers.IO) { old.forEach { File(renderDir(gameId), it).delete() } }
    }

    /** Elimina cartas, renumera y rebaraja los tableros. */
    suspend fun deleteCards(gameId: String, ids: Set<String>): Int {
        var removed = emptyList<CardSpec>()
        editGame(gameId) { g ->
            removed = g.cards.filter { it.id in ids }
            withBoards(g.copy(cards = g.cards.filterNot { it.id in ids }))
        }
        withContext(Dispatchers.IO) {
            removed.forEach { c ->
                File(renderDir(gameId), c.render).delete()
                c.source?.let { File(srcDir(gameId), it).delete() }
            }
        }
        return removed.size
    }

    /** Reparte de nuevo al azar; si faltan cartas para el mínimo, los tableros quedan vacíos. */
    private fun withBoards(g: Game): Game {
        val ids = g.cards.map { it.id }
        val enough = ids.size >= Boards.minimumCards(g.boardCount, g.cells)
        val boards = if (enough) Boards.generate(ids, g.boardCount, g.cells) else null
        return g.copy(boards = boards ?: emptyList(), boardsAt = boards?.let { System.currentTimeMillis() })
    }

    // ---------- Persistencia ----------

    private suspend fun editGame(id: String, change: (Game) -> Game) = update { list ->
        withContext(Dispatchers.Default) { list.map { if (it.id == id) change(it) else it } } to Unit
    }

    private suspend fun <T> update(change: suspend (List<Game>) -> Pair<List<Game>, T>): T = mutex.withLock {
        val (next, result) = change(_games.value)
        withContext(Dispatchers.IO) { write(next) }
        _games.value = next
        result
    }

    private fun write(list: List<Game>) {
        val tmp = File(index.parentFile, "${index.name}.tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(index)) {
            index.delete()
            tmp.renameTo(index)
        }
    }

    // ---------- Migración desde la v1 (cards.json) ----------

    @Serializable
    private data class LegacyCard(val id: String, val fileName: String, val addedAt: Long)

    @Serializable
    private data class LegacyAsset(val hashes: Map<String, Int>, val boards: List<List<Int>>)

    /**
     * Las cartas de la v1 pasan tal cual a "Mi primera lotería". Si son las 54 cartas originales
     * (se reconocen por su huella SHA-256), conservan los tableros ya impresos.
     */
    private fun migrateLegacy(): List<Game> {
        val legacyIndex = File(app.filesDir, "cards.json")
        val legacyDir = File(app.filesDir, "cards")
        if (!legacyIndex.exists()) return emptyList()
        val legacy = runCatching { json.decodeFromString<List<LegacyCard>>(legacyIndex.readText()) }.getOrDefault(emptyList())
            .filter { File(legacyDir, it.fileName).exists() }
        if (legacy.isEmpty()) return emptyList()

        val asset = runCatching {
            app.assets.open("legacy/legacy.json").use { json.decodeFromString<LegacyAsset>(it.readBytes().decodeToString()) }
        }.getOrNull()
        val gameId = UUID.randomUUID().toString()
        val render = renderDir(gameId)
        val numbered = legacy.map { c ->
            val file = File(legacyDir, c.fileName)
            val number = asset?.hashes?.get(sha256(file))
            val ext = file.extension.ifEmpty { "jpg" }
            val name = "${c.id}_v1.$ext"
            file.copyTo(File(render, name), overwrite = true)
            number to CardSpec(c.id, CardKind.IMAGE, name, addedAt = c.addedAt)
        }.sortedWith(compareBy({ it.first ?: Int.MAX_VALUE }, { it.second.addedAt }))

        val cards = numbered.map { it.second }
        val byNumber = numbered.mapNotNull { (n, c) -> n?.let { it to c.id } }.toMap()
        val keepBoards = asset != null && byNumber.size == 54 && cards.size == 54
        var game = Game(gameId, "Mi primera lotería", 24, 4, cards, createdAt = System.currentTimeMillis())
        game = if (keepBoards) {
            game.copy(boards = asset!!.boards.map { b -> b.map { byNumber.getValue(it) } }, boardsAt = System.currentTimeMillis())
        } else {
            withBoards(game)
        }
        legacyDir.deleteRecursively()
        legacyIndex.delete()
        return listOf(game)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Volatile private var instance: GameRepository? = null

        fun get(context: Context): GameRepository =
            instance ?: synchronized(this) { instance ?: GameRepository(context).also { instance = it } }
    }
}
