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
import fr.rabuxx.iptvtv.PinPurpose
import fr.rabuxx.iptvtv.UiState
import fr.rabuxx.iptvtv.UpdateState
import fr.rabuxx.iptvtv.data.Category
import fr.rabuxx.iptvtv.data.FAVORITES_CATEGORY
import fr.rabuxx.iptvtv.data.LATEST_CATEGORY
import fr.rabuxx.iptvtv.data.ListFilter
import fr.rabuxx.iptvtv.data.Ranking
import fr.rabuxx.iptvtv.data.REPLAY_CATEGORY
import fr.rabuxx.iptvtv.data.SPORT_CATEGORY
import fr.rabuxx.iptvtv.data.MULTI_CATEGORY
import fr.rabuxx.iptvtv.data.PhoneLinkMode
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import fr.rabuxx.iptvtv.data.nowPlaying
import fr.rabuxx.iptvtv.data.progress
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.rabuxx.iptvtv.data.Section
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FavoritesChip = Category(FAVORITES_CATEGORY, "★ Favoris")
private val ReplayChip = Category(REPLAY_CATEGORY, "⟲ Replay")
private val LatestChip = Category(LATEST_CATEGORY, "✦ Dernières sorties")
private val SportChip = Category(SPORT_CATEGORY, "⚽ Sport ce soir")
private val MultiChip = Category(MULTI_CATEGORY, "▦ Multi-écran")

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
            sleepEndsAt = state.sleepEndsAt,
            onSelect = vm::selectSection,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.section) {
                Section.Home -> HomeContent(state, vm, restoreHere, onRestored)

                Section.Search -> SearchContent(state, vm, restoreHere, onRestored)

                Section.Live -> {
                    val replayMode = state.live.selectedId == REPLAY_CATEGORY
                    val epg by vm.epg.collectAsStateWithLifecycle()
                    val preview = rememberChannelPreview(enabled = state.previewEnabled && !replayMode, urlFor = vm::liveUrl)
                    CatalogContent(
                        catalog = state.live,
                        section = Section.Live,
                        state = state,
                        onLongPressCategory = { vm.openCategoryOptions(Section.Live, it) },
                        extraCategories = listOfNotNull(SportChip, MultiChip, FavoritesChip, ReplayChip.takeIf { state.hasReplay }),
                        columns = 4,
                        emptyText = when (state.live.selectedId) {
                            FAVORITES_CATEGORY -> "Aucune chaîne favorite.\nMaintiens OK sur une chaîne pour l'ajouter."
                            REPLAY_CATEGORY -> if (state.libraryLoading) "Chargement…" else "Aucune chaîne avec replay"
                            else -> "Aucune chaîne dans cette catégorie"
                        },
                        restore = restoreHere,
                        onRestored = onRestored,
                        onSelectCategory = { id ->
                            when (id) {
                                SPORT_CATEGORY -> vm.openSport()
                                MULTI_CATEGORY -> vm.openMultiView()
                                else -> vm.selectCategory(Section.Live, id)
                            }
                        },
                        onRetry = { vm.retry(Section.Live) },
                        onReload = { vm.reload(Section.Live) },
                        itemsLabel = { n -> if (n > 1) "$n chaînes" else "$n chaîne" },
                    ) { index, channel, requester ->
                        // Programme en cours, demandé quand la carte apparaît
                        LaunchedEffect(channel.streamId) { if (!replayMode) vm.requestEpg(channel) }
                        val now = epg[channel.streamId]?.nowPlaying()
                        MediaCard(
                            title = channel.name,
                            image = channel.icon,
                            aspectRatio = 16f / 9f,
                            contentScale = ContentScale.Fit,
                            imagePadding = 14.dp,
                            subtitle = when {
                                replayMode -> "Replay ${channel.archiveDays} j"
                                now != null -> now.title
                                else -> channel.number.takeIf { it.isNotBlank() }?.let { "Chaîne $it" }
                            },
                            progress = now?.progress() ?: 0f,
                            onFocusChange = { focused -> preview.onFocus(channel, focused) },
                            overlay = if (preview.isShowing(channel)) {
                                { PreviewVideo(preview) }
                            } else {
                                null
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
                    section = Section.Movies,
                    state = state,
                    onLongPressCategory = { vm.openCategoryOptions(Section.Movies, it) },
                    extraCategories = listOfNotNull(LatestChip.takeIf { state.preferFrench }, FavoritesChip),
                    columns = 6,
                    emptyText = when (state.movies.selectedId) {
                        FAVORITES_CATEGORY -> "Aucun film favori.\nMaintiens OK sur une affiche pour l'ajouter."
                        LATEST_CATEGORY -> if (state.libraryLoading || state.library == null) "Chargement des dernières sorties…" else "Aucune sortie récente"
                        else -> if (state.movieFilter.minRating > 0) "Aucun film noté ${state.movieFilter.ratingLabel} ici" else "Aucun film dans cette catégorie"
                    },
                    restore = restoreHere,
                    onRestored = onRestored,
                    onSelectCategory = { vm.selectCategory(Section.Movies, it) },
                    onRetry = { vm.retry(Section.Movies) },
                    onReload = { vm.reload(Section.Movies) },
                    itemsLabel = { n -> if (n > 1) "$n films" else "$n film" },
                    filter = state.movieFilter,
                    onCycleSort = { vm.cycleSort(Section.Movies) },
                    onCycleRating = { vm.cycleMinRating(Section.Movies) },
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
                    section = Section.Series,
                    state = state,
                    onLongPressCategory = { vm.openCategoryOptions(Section.Series, it) },
                    extraCategories = listOfNotNull(LatestChip.takeIf { state.preferFrench }, FavoritesChip),
                    columns = 6,
                    emptyText = when (state.series.selectedId) {
                        FAVORITES_CATEGORY -> "Aucune série favorite.\nMaintiens OK sur une affiche pour l'ajouter."
                        LATEST_CATEGORY -> if (state.libraryLoading || state.library == null) "Chargement des dernières sorties…" else "Aucune série récente"
                        else -> if (state.seriesFilter.minRating > 0) "Aucune série notée ${state.seriesFilter.ratingLabel} ici" else "Aucune série dans cette catégorie"
                    },
                    restore = restoreHere,
                    onRestored = onRestored,
                    onSelectCategory = { vm.selectCategory(Section.Series, it) },
                    onRetry = { vm.retry(Section.Series) },
                    onReload = { vm.reload(Section.Series) },
                    itemsLabel = { n -> if (n > 1) "$n séries" else "$n série" },
                    filter = state.seriesFilter,
                    onCycleSort = { vm.cycleSort(Section.Series) },
                    onCycleRating = { vm.cycleMinRating(Section.Series) },
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
private fun TopBar(
    selected: Section,
    tabFocus: FocusRequester,
    updateBadge: Boolean,
    sleepEndsAt: Long?,
    onSelect: (Section) -> Unit,
) {
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
        Clock(sleepEndsAt, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Clock(sleepEndsAt: Long?, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val sleep = sleepEndsAt?.let { end ->
        val minutes = ((end - now.time) / 60_000).coerceAtLeast(0)
        "☾ ${if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"}  ·  "
    }.orEmpty()
    Text(
        sleep + SimpleDateFormat("HH:mm", Locale.FRANCE).format(now),
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
    section: Section,
    state: UiState,
    onLongPressCategory: (Category) -> Unit,
    extraCategories: List<Category>,
    columns: Int,
    emptyText: String,
    restore: Boolean,
    onRestored: (Boolean) -> Unit,
    onSelectCategory: (String) -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    itemsLabel: (Int) -> String,
    filter: ListFilter? = null,
    onCycleSort: () -> Unit = {},
    onCycleRating: () -> Unit = {},
    item: @Composable (index: Int, item: T, focusRequester: FocusRequester?) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (catalog.categories.isNotEmpty()) {
            CategoryRow(
                categories = extraCategories + catalog.categories,
                selectedId = catalog.selectedId,
                isLocked = { id -> state.isLocked(section, id) },
                onSelect = onSelectCategory,
                onLongPress = onLongPressCategory,
            )
        }
        Toolbar(
            count = catalog.items.size.takeIf { !catalog.loading || it > 0 },
            itemsLabel = itemsLabel,
            filter = filter,
            refreshing = catalog.refreshing,
            onCycleSort = onCycleSort,
            onCycleRating = onCycleRating,
            onReload = onReload,
        )
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

/** Tri, note minimale, nombre d'éléments et bouton d'actualisation, au-dessus de la grille. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Toolbar(
    count: Int?,
    itemsLabel: (Int) -> String,
    filter: ListFilter?,
    refreshing: Boolean,
    onCycleSort: () -> Unit,
    onCycleRating: () -> Unit,
    onReload: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .padding(bottom = 4.dp)
            .focusRestorer()
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filter != null) {
            ChipButton(label = "Tri : ${filter.sort.label}", selected = false, onClick = onCycleSort)
            ChipButton(
                label = "Note : ${filter.ratingLabel}",
                selected = filter.minRating > 0,
                onClick = onCycleRating,
            )
        }
        Box(Modifier.weight(1f)) {
            if (count != null) {
                Text(
                    itemsLabel(count),
                    modifier = Modifier.padding(start = 8.dp),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        ChipButton(
            label = if (refreshing) "Actualisation…" else "↻ Actualiser",
            selected = refreshing,
            onClick = { if (!refreshing) onReload() },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoryRow(
    categories: List<Category>,
    selectedId: String?,
    isLocked: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onLongPress: (Category) -> Unit,
) {
    LazyRow(
        modifier = Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories, key = { i, c -> "${c.id}-$i" }) { _, category ->
            ChipButton(
                label = if (isLocked(category.id)) "🔒 ${category.name}" else category.name,
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                // Appui long : masquer ou verrouiller la catégorie
                onLongClick = { onLongPress(category) },
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
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // --- Compte et application ---
        SettingsColumn(Modifier.weight(1f)) {
            SectionTitle("Compte")
            state.savedCredentials?.let {
                Text(it.username, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(it.baseUrl, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            state.account?.let {
                val exp = it.expiresAt?.let { s ->
                    "Expire le " + SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(s * 1000))
                } ?: "Sans date d'expiration"
                Text(exp, color = TextSecondary, fontSize = 12.sp)
                it.maxConnections?.let { m -> Text("Connexions simultanées : $m", color = TextSecondary, fontSize = 12.sp) }
            }
            SettingButton("Se déconnecter", onClick = vm::logout)

            SectionTitle("Application", Modifier.padding(top = 10.dp))
            Text("Rabux TV · version ${vm.appVersion}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            when (val u = state.update) {
                UpdateState.Checking -> Text("Recherche d'une mise à jour…", color = TextSecondary, fontSize = 12.sp)
                is UpdateState.Available -> {
                    Text("La version ${u.info.version} est disponible.", fontSize = 12.sp)
                    SettingButton("Installer la ${u.info.version}", onClick = { vm.installUpdate(u.info) })
                }
                is UpdateState.Downloading -> Text(
                    "Téléchargement… ${(u.progress * 100).toInt()} %",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                is UpdateState.NeedsPermission -> {
                    Text(
                        if (u.opened) {
                            "Autorise « Rabux TV » à installer des applications, puis reviens ici."
                        } else {
                            "Autorise « Rabux TV » dans Paramètres › Applications › Accès spécial › Installer des applis inconnues."
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    SettingButton("Réessayer l'installation", onClick = { vm.installUpdate(u.info) })
                }
                else -> {
                    when (u) {
                        UpdateState.UpToDate -> Text("L'app est à jour.", color = TextSecondary, fontSize = 12.sp)
                        is UpdateState.Error -> Text(u.message, color = ErrorRed, fontSize = 12.sp)
                        else -> Unit
                    }
                    SettingButton("Rechercher une mise à jour", onClick = { vm.checkForUpdate() })
                }
            }
            SettingButton(
                if (state.betaChannel) "Canal : bêta" else "Canal : stable",
                onClick = { vm.setBetaChannel(!state.betaChannel) },
            )
            SettingHint("En bêta, tu reçois les nouveautés en avant-première (versions « -beta »), parfois moins stables.")
            if (state.crashReportsAvailable) {
                SettingButton(
                    if (state.crashReports) "Rapports de plantage : activés" else "Rapports de plantage : désactivés",
                    onClick = { vm.setCrashReports(!state.crashReports) },
                )
                SettingHint("Envoie le détail technique d'un plantage, sans identifiants ni adresse du serveur.")
            }
        }

        // --- Préférences ---
        SettingsColumn(Modifier.weight(1f)) {
            SectionTitle("Préférences")
            SettingButton("▤  Mon bilan de visionnage", onClick = vm::openStats)
            SettingButton(
                if (state.preferFrench) "France en priorité : activé" else "France en priorité : désactivé",
                onClick = { vm.setPreferFrench(!state.preferFrench) },
            )
            SettingHint("Sport FR en tête du direct, FR puis VOSTFR et dernières sorties pour les films et séries.")
            SettingButton(
                if (state.previewEnabled) "Aperçu des chaînes : activé" else "Aperçu des chaînes : désactivé",
                onClick = { vm.setPreviewEnabled(!state.previewEnabled) },
            )
            SettingHint("Vidéo muette dans la carte d'une chaîne après un instant. À désactiver si ton abonnement n'autorise qu'une connexion.")
            SettingButton(
                when (state.sleepMinutes) {
                    0 -> "Minuteur de veille : désactivé"
                    else -> "Minuteur de veille : ${state.sleepMinutes} min"
                },
                onClick = vm::cycleSleepTimer,
            )
            SettingHint("Chaque appui passe à 30, 60, 90, 120 min. L'app s'arrête à la fin, après un avertissement.")
            SettingButton(
                if (state.screensaverMinutes == 0) "Économiseur d'écran : désactivé" else "Économiseur d'écran : après ${state.screensaverMinutes} min",
                onClick = vm::cycleScreensaver,
            )
            SettingButton("Voir l'économiseur", onClick = vm::startScreensaver)
            SettingButton(
                if (state.libraryLoading) "Actualisation du catalogue…" else "↻ Actualiser tout le catalogue",
                onClick = { if (!state.libraryLoading) vm.reloadAll() },
            )
        }

        // --- Fiches enrichies et contrôle parental ---
        SettingsColumn(Modifier.weight(1f)) {
            SectionTitle("Fiches enrichies")
            Text(
                if (state.tmdbAvailable) "TMDB activé" else "TMDB non configuré",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            SettingHint("Bandes-annonces, casting et recommandations « disponibles chez toi » grâce à TMDB (clé gratuite).")
            SettingButton(
                if (state.tmdbAvailable) "Changer la clé TMDB (téléphone)" else "Ajouter une clé TMDB (téléphone)",
                onClick = { vm.openPhoneEntry(PhoneLinkMode.Tmdb) },
            )

            SectionTitle("Contrôle parental", Modifier.padding(top = 10.dp))
            Text(
                if (state.hasPin) "Code parental activé" else "Aucun code parental",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.hasPin) {
                SettingButton("Supprimer le code", onClick = { vm.requestPin(PinPurpose.RemovePin) })
            } else {
                SettingButton("Créer un code", onClick = { vm.requestPin(PinPurpose.Create()) })
            }
            SettingHint("Maintiens OK sur une pastille de catégorie pour la masquer ou la verrouiller avec le code.")
            val hidden = state.hiddenCategories.size
            val locked = state.lockedCategories.size
            Text(
                "$hidden masquée${if (hidden > 1) "s" else ""} · $locked verrouillée${if (locked > 1) "s" else ""}",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            if (hidden + locked > 0) {
                SettingButton("Tout réafficher", onClick = vm::resetCategories)
            }
        }
    }
}

/** Colonne de réglages défilante : le focus fait apparaître les boutons hors écran. */
@Composable
private fun SettingsColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SettingButton(label: String, onClick: () -> Unit) {
    ChipButton(label = label, selected = false, onClick = onClick)
}

@Composable
private fun SettingHint(text: String) {
    Text(text, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
}

/** Largeur des cartes dans les rangées horizontales (accueil, recherche). */
val PosterWidth = 112.dp
val WideCardWidth = 180.dp
