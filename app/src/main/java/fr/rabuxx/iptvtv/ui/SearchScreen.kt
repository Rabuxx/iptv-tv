package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.rabuxx.iptvtv.MainViewModel
import fr.rabuxx.iptvtv.UiState

/** Onglet Rechercher : un champ, puis des rangées Chaînes / Films / Séries. */
@Composable
fun SearchContent(state: UiState, vm: MainViewModel, restore: Boolean, onRestored: (Boolean) -> Unit) {
    val focus = rememberShelfFocus(restore, onRestored)
    val focusManager = LocalFocusManager.current
    // Texte local : la saisie reste fluide même si la recherche prend quelques millisecondes
    var text by rememberSaveable { mutableStateOf(state.search.query) }
    val result = state.search

    Column(Modifier.fillMaxSize()) {
        TvTextField(
            label = null,
            hint = "Chaînes, films, séries…",
            value = text,
            onValueChange = {
                text = it
                vm.search(it)
            },
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp).width(480.dp),
            imeAction = ImeAction.Search,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                text.isBlank() -> CenteredMessage("Tape le nom d'une chaîne, d'un film ou d'une série.")
                state.library == null -> CenteredMessage("Chargement du catalogue…")
                result.query == text && result.isEmpty -> CenteredMessage("Aucun résultat pour « $text »")
                else -> LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (result.channels.isNotEmpty()) item("channels") {
                        ChannelShelf("Chaînes", "sc", result.channels, state, vm, focus)
                    }
                    if (result.movies.isNotEmpty()) item("movies") {
                        MovieShelf("Films", "sm", result.movies, state, vm, focus)
                    }
                    if (result.series.isNotEmpty()) item("series") {
                        SeriesShelf("Séries", "ss", result.series, state, vm, focus)
                    }
                }
            }
        }
    }
}
