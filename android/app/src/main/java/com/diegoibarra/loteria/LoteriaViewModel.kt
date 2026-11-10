package com.diegoibarra.loteria

import android.app.Application
import android.content.res.Resources
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.diegoibarra.loteria.data.Card
import com.diegoibarra.loteria.data.CardRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/** Mensajes breves para el Snackbar de la galería. */
sealed interface UiMessage {
    fun text(res: Resources): String

    data class Added(val count: Int) : UiMessage {
        override fun text(res: Resources) = res.getQuantityString(R.plurals.added_message, count, count)
    }

    data class Failed(val count: Int) : UiMessage {
        override fun text(res: Resources) = res.getQuantityString(R.plurals.failed_message, count, count)
    }

    data class Deleted(val count: Int) : UiMessage {
        override fun text(res: Resources) = res.getQuantityString(R.plurals.deleted_message, count, count)
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

/**
 * Estado de la app. El orden del mazo, la carta actual, la pantalla y la selección viven en
 * SavedStateHandle para sobrevivir a rotaciones y a que el sistema cierre el proceso.
 */
class LoteriaViewModel(
    application: Application,
    private val handle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repo = CardRepository.get(application)

    val cards: StateFlow<List<Card>> = repo.cards
    val loaded: StateFlow<Boolean> = repo.loaded

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val selectionMode: StateFlow<Boolean> = handle.getStateFlow(KEY_SELECTION_MODE, false)
    val selected: StateFlow<List<String>> = handle.getStateFlow(KEY_SELECTED, ArrayList<String>())

    val inGame: StateFlow<Boolean> = handle.getStateFlow(KEY_IN_GAME, false)
    val deckSeed: StateFlow<Long> = handle.getStateFlow(KEY_SEED, 0L)
    private val deckIds: StateFlow<List<String>> = handle.getStateFlow(KEY_DECK, ArrayList<String>())

    /** El mazo barajado resuelto a cartas existentes. */
    val deck: StateFlow<List<Card>> = combine(cards, deckIds) { all, ids ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentPage: Int get() = handle[KEY_PAGE] ?: 0

    init {
        viewModelScope.launch {
            repo.ensureLoaded()
            val existing = cards.value.mapTo(HashSet()) { it.id }
            if (inGame.value && deckIds.value.none { it in existing }) exitGame()
        }
    }

    fun fileFor(card: Card): File = repo.fileFor(card)

    // ---------- Galería ----------

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            try {
                val result = repo.import(uris)
                if (result.added > 0) messageChannel.send(UiMessage.Added(result.added))
                if (result.failed > 0) messageChannel.send(UiMessage.Failed(result.failed))
            } finally {
                _importing.value = false
            }
        }
    }

    fun onCardLongPress(id: String) {
        if (selectionMode.value) {
            toggleSelection(id)
        } else {
            handle[KEY_SELECTION_MODE] = true
            handle[KEY_SELECTED] = arrayListOf(id)
        }
    }

    fun toggleSelection(id: String) {
        val current = ArrayList(selected.value)
        if (!current.remove(id)) current.add(id)
        handle[KEY_SELECTED] = current
    }

    fun enterSelection() {
        handle[KEY_SELECTION_MODE] = true
        handle[KEY_SELECTED] = ArrayList<String>()
    }

    fun toggleSelectAll() {
        val all = cards.value.map { it.id }
        handle[KEY_SELECTED] = if (selected.value.toSet().containsAll(all)) ArrayList() else ArrayList(all)
    }

    fun exitSelection() {
        handle[KEY_SELECTION_MODE] = false
        handle[KEY_SELECTED] = ArrayList<String>()
    }

    fun deleteSelected() {
        val ids = selected.value.toSet()
        exitSelection()
        viewModelScope.launch {
            val n = repo.delete(ids)
            if (n > 0) messageChannel.send(UiMessage.Deleted(n))
        }
    }

    fun deleteAll() {
        exitSelection()
        viewModelScope.launch {
            val n = repo.deleteAll()
            if (n > 0) messageChannel.send(UiMessage.Deleted(n))
        }
    }

    // ---------- Juego ----------

    fun startGame() {
        val ids = cards.value.map { it.id }
        if (ids.isEmpty()) return
        exitSelection()
        newDeck(ids)
        handle[KEY_IN_GAME] = true
    }

    fun restart() = newDeck(cards.value.map { it.id })

    fun onPageSettled(page: Int) {
        handle[KEY_PAGE] = page
    }

    fun exitGame() {
        handle[KEY_IN_GAME] = false
        handle[KEY_DECK] = ArrayList<String>()
        handle[KEY_PAGE] = 0
    }

    private fun newDeck(ids: List<String>) {
        handle[KEY_DECK] = ArrayList(fisherYates(ids))
        handle[KEY_PAGE] = 0
        handle[KEY_SEED] = System.nanoTime()
    }

    private companion object {
        const val KEY_SELECTION_MODE = "selection_mode"
        const val KEY_SELECTED = "selected"
        const val KEY_IN_GAME = "in_game"
        const val KEY_DECK = "deck"
        const val KEY_PAGE = "page"
        const val KEY_SEED = "deck_seed"
    }
}
