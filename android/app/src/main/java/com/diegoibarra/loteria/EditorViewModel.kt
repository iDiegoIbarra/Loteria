package com.diegoibarra.loteria

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.diegoibarra.loteria.data.CardDesign
import com.diegoibarra.loteria.data.CardDraft
import com.diegoibarra.loteria.data.CardSpec
import com.diegoibarra.loteria.data.GameRepository
import com.diegoibarra.loteria.render.CardTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Editor de cartas. Con varias imágenes trabaja como cola ("Carta 3 de 20"): todas llegan con
 * colores automáticos y se guardan juntas. La cola vive en SavedStateHandle.
 */
class EditorViewModel(application: Application, private val handle: SavedStateHandle) : AndroidViewModel(application) {

    private val repo = GameRepository.get(application)
    private val json = Json { ignoreUnknownKeys = true }

    private val _drafts = MutableStateFlow(restore())
    val drafts: StateFlow<List<CardDraft>> = _drafts.asStateFlow()

    val index: StateFlow<Int> = handle.getStateFlow(KEY_INDEX, 0)
    val gameId: String? get() = handle[KEY_GAME]

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _image = MutableStateFlow<Bitmap?>(null)
    val image: StateFlow<Bitmap?> = _image.asStateFlow()

    /** Se muestra una vez por sesión del editor si la imagen no tiene transparencia. */
    private val _showPngTip = MutableStateFlow(false)
    val showPngTip: StateFlow<Boolean> = _showPngTip.asStateFlow()
    private var tipShown = false

    private val cache = object : LruCache<String, Bitmap>(3) {}
    private var loadJob: Job? = null

    init {
        loadCurrent()
    }

    val isNew: Boolean get() = _drafts.value.any { !it.existing }

    fun startNew(gameId: String, uris: List<Uri>) {
        reset(gameId)
        _loading.value = true
        viewModelScope.launch {
            val names = repo.importSources(gameId, uris)
            val drafts = withContext(Dispatchers.Default) {
                names.mapNotNull { name ->
                    val small = CardTemplate.decode(repo.sourceFile(gameId, name), 512) ?: return@mapNotNull null
                    CardDraft(name.substringBefore('.'), name, CardTemplate.autoDesign(small)).also { small.recycle() }
                }
            }
            setDrafts(drafts)
            _loading.value = false
            loadCurrent()
        }
    }

    fun startEdit(gameId: String, card: CardSpec) {
        val source = card.source ?: return
        val design = card.design ?: return
        reset(gameId)
        setDrafts(listOf(CardDraft(card.id, source, design, existing = true)))
        loadCurrent()
    }

    private fun reset(gameId: String) {
        handle[KEY_GAME] = gameId
        handle[KEY_INDEX] = 0
        tipShown = false
        _showPngTip.value = false
        _image.value = null
        cache.evictAll()
    }

    fun update(change: (CardDesign) -> CardDesign) {
        val i = index.value
        val list = _drafts.value
        if (i !in list.indices) return
        setDrafts(list.toMutableList().also { it[i] = it[i].copy(design = change(it[i].design)) })
    }

    fun goTo(i: Int) {
        if (i !in _drafts.value.indices) return
        handle[KEY_INDEX] = i
        loadCurrent()
    }

    fun dismissPngTip() {
        _showPngTip.value = false
    }

    /** Guarda todas las cartas de la cola. Devuelve cuántas son nuevas. */
    suspend fun save(): Int {
        val id = gameId ?: return 0
        val list = _drafts.value
        repo.saveDrafts(id, list)
        clear()
        return list.count { !it.existing }
    }

    /** Descarta la cola; las imágenes copiadas de cartas nuevas se borran. */
    fun cancel() {
        val id = gameId
        if (id != null) repo.discardSources(id, _drafts.value.filterNot { it.existing }.map { it.source })
        clear()
    }

    private fun clear() {
        setDrafts(emptyList())
        _image.value = null
        cache.evictAll()
    }

    private fun loadCurrent() {
        val id = gameId ?: return
        val draft = _drafts.value.getOrNull(index.value) ?: return
        loadJob?.cancel()
        cache.get(draft.source)?.let {
            _image.value = it
            checkTip(it)
            return
        }
        _image.value = null
        loadJob = viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { CardTemplate.decode(repo.sourceFile(id, draft.source), 2048) }
            if (bmp != null) {
                cache.put(draft.source, bmp)
                _image.value = bmp
                checkTip(bmp)
            }
        }
    }

    private fun checkTip(bmp: Bitmap) {
        if (tipShown) return
        viewModelScope.launch {
            val transparent = withContext(Dispatchers.Default) { CardTemplate.hasTransparency(bmp) }
            if (!transparent && !tipShown) {
                tipShown = true
                _showPngTip.value = true
            }
        }
    }

    private fun setDrafts(list: List<CardDraft>) {
        _drafts.value = list
        handle[KEY_DRAFTS] = json.encodeToString(list)
    }

    private fun restore(): List<CardDraft> =
        handle.get<String>(KEY_DRAFTS)?.let { runCatching { json.decodeFromString<List<CardDraft>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY_GAME = "editor_game"
        const val KEY_INDEX = "editor_index"
        const val KEY_DRAFTS = "editor_drafts"
    }
}
