package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import fr.rabuxx.iptvtv.Catalog
import fr.rabuxx.iptvtv.MainViewModel
import fr.rabuxx.iptvtv.UiState
import fr.rabuxx.iptvtv.UpdateState
import fr.rabuxx.iptvtv.data.Category
import fr.rabuxx.iptvtv.data.FAVORITES_CATEGORY
import fr.rabuxx.iptvtv.data.LATEST_CATEGORY
import fr.rabuxx.iptvtv.data.Ranking
import fr.rabuxx.iptvtv.data.REPLAY_CATEGORY
import fr.rabuxx.iptvtv.data.Section
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FavoritesChip = Category(FAVORITES_CATEGORY, "★ Favoris")
private val ReplayChip = Category(REPLAY_CATEGORY, "⟲ Replay")
private val LatestChip = Category(LATEST_CATEGORY, "✦ Dernières sorties")

/** Écran principal : barre d'onglets façon tvOS + contenu de la section active. */
@Composable
fun MainScreen(state: UiState, vm: MainViewModel) {
    val tabFocus = remember { FocusRequester() }
    val restoreHere = state.restoreFocus == state.section

    // Au premier affichage (pas de focus à restaurer), le focus part sur l'onglet actif
    LaunchedEffect(Unit) {
        if (state.restoreFocus == null) {
            withFrameNanos { }
            runCatching { tabFocus.requestFocus() }
        }
    }

    val onRestored: (Boolean) -> Unit = { ok ->
        if (!ok) runCatching { tabFocus.requestFocus() }
        vm.consumeRestoreFocus()
    }

    // Catégories VOSTFR : la mention s'affiche sous les affiches concernées
    val vostMovies = remember(state.movies.categories) { Ranking.vostfrCategoryIds(state.movies.categories) }
    val vostSeries = remember(state.series.categories) { Ranking.vostfrCategoryIds(state.series.categories) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            selected = state.section,
            tabFocus = tabFocus,
            updateBadge = state.update is UpdateState.Available,
            onSelect = vm::selectSection,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.section) {
                Section.Home -> HomeContent(state, vm, restoreHere, onRestored)

                Section.Search -> SearchContent(state, vm, restoreHere, onRestored)

                Section.Live -> {
                    val replayMode = state.live.selectedId == REPLAY_CATEGORY
                    CatalogContent(
                        catalog = state.live,
                        extraCategories = listOfNotNull(FavoritesChip, ReplayChip.takeIf { state.hasReplay }),
                        columns = 4,
                        emptyText = when (state.live.selectedId) {
                            FAVORITES_CATEGORY -> "Aucune chaîne favorite.\nMaintiens OK sur une chaîne pour l'ajouter."
                            REPLAY_CATEGORY -> if (state.libraryLoading) "Chargement…" else "Aucune chaîne avec replay"
                            else -> "Aucune chaîne dans cette catégorie"
                        },
                        restore = restoreHere,
                        onRestored = onRestored,
                        onSelectCategory = { vm.selectCategory(Section.Live, it) },
                        onRetry = { vm.retry(Section.Live) },
                    ) { index, channel, requester ->
                        MediaCard(
                            title = channel.name,
                            image = channel.icon,
                            aspectRatio = 16f / 9f,
                            contentScale = ContentScale.Fit,
                            imagePadding = 14.dp,
                            subtitle = if (replayMode) {
                                "Replay ${channel.archiveDays} j"
                            } else {
                                channel.number.takeIf { it.isNotBlank() }?.let { "Chaîne $it" }
                            },
                            focusRequester = requester,
                            favorite = state.favorites.has(channel),
                            onLongClick = { vm.toggleFavorite(channel) },
                            onClick = {
                                if (replayMode) vm.openReplay(channel, index)
                                else vm.playChannels(state.live.items, index, fromGrid = true)
                            },
                        )
                    }
                }

                Section.Movies -> CatalogContent(
                    catalog = state.movies,
                    extraCategories = listOfNotNull(LatestChip.takeIf { state.preferFrench }, FavoritesChip),
                    columns = 6,
                    emptyText = when (state.movies.selectedId) {
                        FAVORITES_CATEGORY -> "Aucun film favori.\nMaintiens OK sur une affiche pour l'ajouter."
                        LATEST_CATEGORY -> if (state.libraryLoading || state.library == null) "Chargement des dernières sorties…" else "Aucune sortie récente"
                        else -> "Aucun film dans cette catégorie"
                    },
                    restore = restoreHere,
                    onRestored = onRestored,
                    onSelectCategory = { vm.selectCategory(Section.Movies, it) },
                    onRetry = { vm.retry(Section.Movies) },
                ) { index, movie, requester ->
                    MediaCard(
                        title = movie.name,
                        image = movie.poster,
                        subtitle = "VOSTFR".takeIf { movie.categoryId in vostMovies },
                        progress = vm.progress(vm.movieKey(movie)),
                        focusRequester = requester,
                        favorite = state.favorites.has(movie),
                        onLongClick = { vm.toggleFavorite(movie) },
                        onClick = { vm.openMovie(movie, index) },
                    )
                }

                Section.Series -> CatalogContent(
                    catalog = state.series,
                    extraCategories = listOfNotNull(LatestChip.takeIf { state.preferFrench }, FavoritesChip),
                    columns = 6,
                    emptyText = when (state.series.selectedId) {
                        FAVORITES_CATEGORY -> "Aucune série favorite.\nMaintiens OK sur une affiche pour l'ajouter."
                        LATEST_CATEGORY -> if (state.libraryLoading || state.library == null) "Chargement des dernières sorties…" else "Aucune série récente"
                        else -> "Aucune série dans cette catégorie"
                    },
                    restore = restoreHere,
                    onRestored = onRestored,
                    onSelectCategory = { vm.selectCategory(Section.Series, it) },
                    onRetry = { vm.retry(Section.Series) },
                ) { index, series, requester ->
                    MediaCard(
                        title = series.name,
                        image = series.cover,
                        subtitle = listOfNotNull("VOSTFR".takeIf { series.categoryId in vostSeries }, series.year)
                            .joinToString(" · ")
                            .ifBlank { null },
                        focusRequester = requester,
                        favorite = state.favorites.has(series),
                        onLongClick = { vm.toggleFavorite(series) },
                        onClick = { vm.openSeries(series, index) },
                    )
                }

                Section.Settings -> SettingsContent(state, vm)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(selected: Section, tabFocus: FocusRequester, updateBadge: Boolean, onSelect: (Section) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 4.dp)
            .padding(ScreenPadding),
    ) {
        Row(
            // En remontant depuis le contenu, le focus revient sur l'onglet actif (et non sur le plus proche)
            Modifier
                .align(Alignment.Center)
                .focusRestorer { tabFocus }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Section.entries.forEach { section ->
                val label = when {
                    section == Section.Search -> "⌕  ${section.label}"
                    section == Section.Settings && updateBadge -> "${section.label} •"
                    else -> section.label
                }
                TabPill(
                    label = label,
                    selected = section == selected,
                    onFocused = { onSelect(section) },
                    modifier = if (section == selected) Modifier.focusRequester(tabFocus) else Modifier,
                )
            }
        }
        Clock(Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Text(
        SimpleDateFormat("HH:mm", Locale.FRANCE).format(now),
        modifier = modifier,
        color = TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )
}

/**
 * Contenu générique d'une section : rangée de catégories en pastilles, puis grille de cartes.
 * [item] reçoit un FocusRequester non nul pour l'élément à re-sélectionner au retour.
 */
@Composable
private fun <T> CatalogContent(
    catalog: Catalog<T>,
    extraCategories: List<Category>,
    columns: Int,
    emptyText: String,
    restore: Boolean,
    onRestored: (Boolean) -> Unit,
    onSelectCategory: (String) -> Unit,
    onRetry: () -> Unit,
    item: @Composable (index: Int, item: T, focusRequester: FocusRequester?) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (catalog.categories.isNotEmpty()) {
            CategoryRow(extraCategories + catalog.categories, catalog.selectedId, onSelectCategory)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                catalog.loading && catalog.items.isEmpty() -> {
                    CenteredMessage("Chargement…")
                    LaunchedEffect(restore) { if (restore) onRestored(false) }
                }
                catalog.error != null -> {
                    CenteredMessage(catalog.error, ErrorRed) { ActionButton("Réessayer", onClick = onRetry) }
                    LaunchedEffect(restore) { if (restore) onRestored(false) }
                }
                catalog.items.isEmpty() -> {
                    CenteredMessage(emptyText)
                    LaunchedEffect(restore) { if (restore) onRestored(false) }
                }
                else -> CardGrid(catalog, columns, restore, onRestored, item)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoryRow(categories: List<Category>, selectedId: String?, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories, key = { i, c -> "${c.id}-$i" }) { _, category ->
            ChipButton(
                label = category.name,
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun <T> CardGrid(
    catalog: Catalog<T>,
    columns: Int,
    restore: Boolean,
    onRestored: (Boolean) -> Unit,
    item: @Composable (index: Int, item: T, focusRequester: FocusRequester?) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val restoreRequester = remember { FocusRequester() }
    val target = catalog.focusIndex?.takeIf { it in catalog.items.indices }

    LaunchedEffect(restore) {
        if (!restore) return@LaunchedEffect
        if (target == null) {
            onRestored(false)
            return@LaunchedEffect
        }
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
            gridState.scrollToItem(target)
        }
        withFrameNanos { }
        onRestored(runCatching { restoreRequester.requestFocus() }.isSuccess)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        // Marges généreuses : les cartes grossissent au focus et ne doivent pas être coupées
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        itemsIndexed(catalog.items, key = { i, _ -> i }) { index, element ->
            item(index, element, if (index == target) restoreRequester else null)
        }
    }
}

@Composable
private fun SettingsContent(state: UiState, vm: MainViewModel) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        // --- Compte ---
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Compte")
            state.savedCredentials?.let {
                Text(it.username, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(it.baseUrl, color = TextSecondary, fontSize = 14.sp)
            }
            state.account?.let {
                val exp = it.expiresAt?.let { s ->
                    "Expire le " + SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(s * 1000))
                } ?: "Sans date d'expiration"
                Text("Abonnement ${it.status.lowercase()} · $exp", color = TextSecondary, fontSize = 14.sp)
                it.maxConnections?.let { m -> Text("Connexions simultanées : $m", color = TextSecondary, fontSize = 14.sp) }
            }
            Box(Modifier.padding(top = 12.dp)) {
                ActionButton("Se déconnecter", onClick = vm::logout)
            }
        }

        // --- Application ---
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Application")
            Text("Rabux TV · version ${vm.appVersion}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Tri : sport FR en tête du direct, FR puis VOSTFR et dernières sorties pour les films et séries.",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Box(Modifier.padding(bottom = 12.dp)) {
                ActionButton(
                    if (state.preferFrench) "France en priorité : activé" else "France en priorité : désactivé",
                    onClick = { vm.setPreferFrench(!state.preferFrench) },
                )
            }

            when (val u = state.update) {
                UpdateState.Checking -> Text("Recherche d'une mise à jour…", color = TextSecondary, fontSize = 14.sp)
                is UpdateState.Available -> {
                    Text("La version ${u.info.version} est disponible.", fontSize = 14.sp)
                    u.info.notes?.let { Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 6) }
                    Box(Modifier.padding(top = 8.dp)) {
                        ActionButton("Installer la ${u.info.version}", onClick = { vm.installUpdate(u.info) })
                    }
                }
                is UpdateState.Downloading -> Text(
                    "Téléchargement… ${(u.progress * 100).toInt()} %",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
                is UpdateState.NeedsPermission -> {
                    Text(
                        if (u.opened) {
                            "Autorise « Rabux TV » à installer des applications, puis reviens ici."
                        } else {
                            "Autorise « Rabux TV » à installer des applications inconnues dans les paramètres de la TV " +
                                "(Applications › Accès spécial › Installer des applis inconnues)."
                        },
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                    Box(Modifier.padding(top = 8.dp)) {
                        ActionButton("Réessayer l'installation", onClick = { vm.installUpdate(u.info) })
                    }
                }
                else -> {
                    when (u) {
                        UpdateState.UpToDate -> Text("L'app est à jour.", color = TextSecondary, fontSize = 14.sp)
                        is UpdateState.Error -> Text(u.message, color = ErrorRed, fontSize = 14.sp)
                        else -> Unit
                    }
                    Box(Modifier.padding(top = 8.dp)) {
                        ActionButton("Rechercher une mise à jour", onClick = { vm.checkForUpdate() })
                    }
                }
            }
        }
    }
}

/** Largeur des cartes dans les rangées horizontales (accueil, recherche). */
val PosterWidth = 112.dp
val WideCardWidth = 180.dp
