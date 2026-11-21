package com.diegoibarra.loteria

import android.app.Application
import android.content.res.Resources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.diegoibarra.loteria.data.Boards
import com.diegoibarra.loteria.data.CardSpec
import com.diegoibarra.loteria.data.Game
import com.diegoibarra.loteria.data.GameRepository
import com.diegoibarra.loteria.render.PdfContent
import com.diegoibarra.loteria.render.PdfExporter
import com.diegoibarra.loteria.render.PdfTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** Mensajes breves para el Snackbar. */
sealed interface UiMessage {
    fun text(res: Resources): String

    data class Added(val count: Int) : UiMessage {
        override fun text(res: Resources) = res.getQuantityString(R.plurals.added_message, count, count)
    }

    data class Deleted(val count: Int) : UiMessage {
        override fun text(res: Resources) = res.getQuantityString(R.plurals.deleted_message, count, count)
    }

    data object Saved : UiMessage {
        override fun text(res: Resources) = res.getString(R.string.saved_message)
    }

    data object ExportFailed : UiMessage {
        override fun text(res: Resources) = res.getString(R.string.export_failed)
    }
}

/** Baraja Fisher–Yates: recorre de atrás hacia adelante e intercambia con una posición aleatoria. */
fun <T> fisherYates(items: List<T>, random: Random = Random.Default): List<T> {
    val deck = items.toMutableList()
    for (i in deck.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = deck[i]
        deck[i] = deck[j]
        deck[j] = tmp
    }
    return deck
}

enum class Route { HOME, GAME, PLAY, EDITOR, VIEWER }

/**
 * Navegación, juegos, validación, juego en curso y exportación. La ruta, la pestaña, la selección
 * y el mazo viven en SavedStateHandle para sobrevivir a rotaciones y al cierre del proceso.
 */
class LoteriaViewModel(application: Application, private val handle: SavedStateHandle) : AndroidViewModel(application) {

    private val repo = GameRepository.get(application)

    val games: StateFlow<List<Game>> = repo.games
    val loaded: StateFlow<Boolean> = repo.loaded

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val route: StateFlow<String> = handle.getStateFlow(KEY_ROUTE, Route.HOME.name)
    val gameId: StateFlow<String?> = handle.getStateFlow(KEY_GAME, null)
    val tab: StateFlow<Int> = handle.getStateFlow(KEY_TAB, 0)
    val viewerIndex: StateFlow<Int> = handle.getStateFlow(KEY_VIEWER, 0)
    val selectionMode: StateFlow<Boolean> = handle.getStateFlow(KEY_SELECTION_MODE, false)
    val selected: StateFlow<List<String>> = handle.getStateFlow(KEY_SELECTED, ArrayList<String>())

    val current: StateFlow<Game?> = combine(games, gameId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------- Mínimo de cartas ----------

    private val _minimums = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val minimums: StateFlow<Map<Long, Int>> = _minimums.asStateFlow()

    fun minimumKey(count: Int, cells: Int): Long = count.toLong() shl 32 or cells.toLong()

    fun requestMinimum(count: Int, cells: Int) {
        val key = minimumKey(count, cells)
        if (key in _minimums.value) return
        viewModelScope.launch {
            val min = withContext(Dispatchers.Default) { Boards.minimumCards(count, cells) }
            _minimums.update { it + (key to min) }
        }
    }

    // ---------- Juego en curso ----------

    val deckSeed: StateFlow<Long> = handle.getStateFlow(KEY_SEED, 0L)
    private val deckIds: StateFlow<List<String>> = handle.getStateFlow(KEY_DECK, ArrayList<String>())
    val deck: StateFlow<List<CardSpec>> = combine(current, deckIds) { g, ids ->
        val byId = g?.cards?.associateBy { it.id } ?: emptyMap()
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currentPage: Int get() = handle[KEY_PAGE] ?: 0

    // ---------- Exportación ----------

    sealed interface ExportState {
        data object Idle : ExportState
        data object Working : ExportState
        data class Done(val file: File) : ExportState
    }

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureLoaded()
            if (route.value != Route.HOME.name && current.value == null) go(Route.HOME)
            if (route.value == Route.PLAY.name && deckIds.value.isEmpty()) go(Route.GAME)
        }
    }

    fun fileFor(gameId: String, card: CardSpec): File = repo.renderFile(gameId, card)

    // ---------- Navegación ----------

    fun go(route: Route) {
        handle[KEY_ROUTE] = route.name
    }

    fun openGame(id: String, tab: Int = 0) {
        exitSelection()
        handle[KEY_GAME] = id
        handle[KEY_TAB] = tab
        go(Route.GAME)
    }

    fun setTab(index: Int) {
        exitSelection()
        handle[KEY_TAB] = index
    }

    fun openViewer(index: Int) {
        handle[KEY_VIEWER] = index
        go(Route.VIEWER)
    }

    fun back() {
        when (Route.valueOf(route.value)) {
            Route.HOME -> Unit
            Route.GAME -> {
                exitSelection()
                go(Route.HOME)
            }
            Route.VIEWER, Route.EDITOR -> go(Route.GAME)
            Route.PLAY -> exitPlay()
        }
    }

    // ---------- Juegos ----------

    fun createGame(name: String, boards: Int, side: Int) {
        viewModelScope.launch { openGame(repo.createGame(name, boards, side).id) }
    }

    fun saveSettings(name: String, boards: Int, side: Int) {
        val id = gameId.value ?: return
        viewModelScope.launch {
            repo.saveSettings(id, name, boards, side)
            messageChannel.send(UiMessage.Saved)
        }
    }

    fun deleteGame() {
        val id = gameId.value ?: return
        go(Route.HOME)
        handle[KEY_GAME] = null
        viewModelScope.launch { repo.deleteGame(id) }
    }

    // ---------- Selección ----------

    fun onCardLongPress(id: String) {
        if (selectionMode.value) {
            toggleSelection(id)
        } else {
            handle[KEY_SELECTION_MODE] = true
            handle[KEY_SELECTED] = arrayListOf(id)
        }
    }

    fun enterSelection() {
        handle[KEY_SELECTION_MODE] = true
        handle[KEY_SELECTED] = ArrayList<String>()
    }

    fun toggleSelection(id: String) {
        val now = ArrayList(selected.value)
        if (!now.remove(id)) now.add(id)
        handle[KEY_SELECTED] = now
    }

    fun toggleSelectAll() {
        val all = current.value?.cards?.map { it.id } ?: return
        handle[KEY_SELECTED] = if (selected.value.toSet().containsAll(all)) ArrayList() else ArrayList(all)
    }

    fun exitSelection() {
        handle[KEY_SELECTION_MODE] = false
        handle[KEY_SELECTED] = ArrayList<String>()
    }

    fun deleteSelected() {
        val id = gameId.value ?: return
        val ids = selected.value.toSet()
        exitSelection()
        viewModelScope.launch {
            val n = repo.deleteCards(id, ids)
            if (n > 0) messageChannel.send(UiMessage.Deleted(n))
        }
    }

    fun notifyAdded(count: Int) {
        viewModelScope.launch { messageChannel.send(UiMessage.Added(count)) }
    }

    // ---------- Jugar ----------

    fun startPlay() {
        val ids = current.value?.cards?.map { it.id }.orEmpty()
        if (ids.isEmpty()) return
        exitSelection()
        newDeck(ids)
        go(Route.PLAY)
    }

    fun restart() = newDeck(current.value?.cards?.map { it.id }.orEmpty())

    fun onPageSettled(page: Int) {
        handle[KEY_PAGE] = page
    }

    fun exitPlay() {
        handle[KEY_DECK] = ArrayList<String>()
        handle[KEY_PAGE] = 0
        go(Route.GAME)
    }

    private fun newDeck(ids: List<String>) {
        handle[KEY_DECK] = ArrayList(fisherYates(ids))
        handle[KEY_PAGE] = 0
        handle[KEY_SEED] = System.nanoTime()
    }

    // ---------- PDF ----------

    fun exportPdf(content: PdfContent, target: PdfTarget) {
        val game = current.value ?: return
        _export.value = ExportState.Working
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { PdfExporter(getApplication()).export(game, content, target) }.getOrNull()
            }
            if (file == null) {
                _export.value = ExportState.Idle
                messageChannel.send(UiMessage.ExportFailed)
            } else {
                _export.value = ExportState.Done(file)
            }
        }
    }

    fun exportHandled() {
        _export.value = ExportState.Idle
    }

    /** Desde el visor: vuelve a Tableros y abre el diálogo de exportar. */
    val exportRequest: StateFlow<Boolean> = handle.getStateFlow(KEY_EXPORT_REQ, false)

    fun requestExport() {
        handle[KEY_TAB] = 1
        handle[KEY_EXPORT_REQ] = true
        go(Route.GAME)
    }

    fun exportRequestHandled() {
        handle[KEY_EXPORT_REQ] = false
    }

    private companion object {
        const val KEY_ROUTE = "route"
        const val KEY_GAME = "game"
        const val KEY_TAB = "tab"
        const val KEY_VIEWER = "viewer"
        const val KEY_SELECTION_MODE = "selection_mode"
        const val KEY_SELECTED = "selected"
        const val KEY_DECK = "deck"
        const val KEY_PAGE = "page"
        const val KEY_SEED = "deck_seed"
        const val KEY_EXPORT_REQ = "export_request"
    }
}
