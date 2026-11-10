package com.diegoibarra.loteria.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
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
import java.util.UUID

/** Una carta de la baraja: una copia privada de la imagen dentro de filesDir/cards. */
@Serializable
data class Card(
    val id: String,
    val fileName: String,
    val addedAt: Long,
)

data class ImportResult(val added: Int, val failed: Int)

/**
 * Repositorio de imágenes. Copia cada imagen elegida al almacenamiento privado de la app y
 * guarda la lista en un índice JSON, así las cartas sobreviven aunque se borre la foto original.
 */
class CardRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val cardsDir = File(appContext.filesDir, "cards").apply { mkdirs() }
    private val indexFile = File(appContext.filesDir, "cards.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun fileFor(card: Card): File = File(cardsDir, card.fileName)

    suspend fun ensureLoaded() = mutex.withLock {
        if (_loaded.value) return@withLock
        val stored = withContext(Dispatchers.IO) {
            val list = runCatching {
                if (indexFile.exists()) json.decodeFromString<List<Card>>(indexFile.readText()) else emptyList()
            }.getOrDefault(emptyList())
            val valid = list.filter { fileFor(it).exists() }
            // Limpia copias huérfanas de importaciones interrumpidas.
            val known = valid.mapTo(HashSet()) { it.fileName }
            cardsDir.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
            if (valid.size != list.size) writeIndex(valid)
            valid
        }
        _cards.value = stored
        _loaded.value = true
    }

    suspend fun import(uris: List<Uri>): ImportResult {
        ensureLoaded()
        val added = mutableListOf<Card>()
        var failed = 0
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val start = System.currentTimeMillis()
            uris.forEachIndexed { index, uri ->
                val id = UUID.randomUUID().toString()
                val ext = resolver.getType(uri)
                    ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                    ?: "jpg"
                val name = "$id.$ext"
                val tmp = File(cardsDir, "$name.tmp")
                val ok = runCatching {
                    resolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(tmp.length() > 0)
                    check(tmp.renameTo(File(cardsDir, name)))
                }.isSuccess
                if (ok) {
                    added += Card(id = id, fileName = name, addedAt = start + index)
                } else {
                    tmp.delete()
                    failed++
                }
            }
        }
        if (added.isNotEmpty()) {
            mutex.withLock {
                val updated = _cards.value + added
                withContext(Dispatchers.IO) { writeIndex(updated) }
                _cards.value = updated
            }
        }
        return ImportResult(added.size, failed)
    }

    suspend fun delete(ids: Set<String>): Int {
        ensureLoaded()
        return mutex.withLock {
            val (removed, kept) = _cards.value.partition { it.id in ids }
            if (removed.isEmpty()) return@withLock 0
            withContext(Dispatchers.IO) {
                writeIndex(kept)
                removed.forEach { fileFor(it).delete() }
            }
            _cards.value = kept
            removed.size
        }
    }

    suspend fun deleteAll(): Int = delete(_cards.value.mapTo(HashSet()) { it.id })

    /** Escritura atómica: archivo temporal y renombrado. */
    private fun writeIndex(list: List<Card>) {
        val tmp = File(indexFile.parentFile, "${indexFile.name}.tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            tmp.renameTo(indexFile)
        }
    }

    companion object {
        @Volatile
        private var instance: CardRepository? = null

        fun get(context: Context): CardRepository =
            instance ?: synchronized(this) {
                instance ?: CardRepository(context).also { instance = it }
            }
    }
}
