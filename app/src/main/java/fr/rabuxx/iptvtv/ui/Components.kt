package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage

/** Onglet de la barre du haut : le contenu change dès que l'onglet prend le focus, comme sur tvOS. */
@Composable
fun TabPill(label: String, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onFocused,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Chip else Color.Transparent,
            contentColor = if (selected) TextPrimary else TextSecondary,
            focusedContainerColor = FocusFill,
            focusedContentColor = FocusText,
            pressedContainerColor = FocusFill,
            pressedContentColor = FocusText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Pastille cliquable (catégories, saisons) : grise au repos, blanche au focus. */
@Composable
fun ChipButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x59FFFFFF) else Chip,
            contentColor = TextPrimary,
            focusedContainerColor = FocusFill,
            focusedContentColor = FocusText,
            pressedContainerColor = FocusFill,
            pressedContentColor = FocusText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bouton d'action d'une fiche (« Lire », « Reprendre »…). */
@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x4DFFFFFF),
            contentColor = TextPrimary,
            focusedContainerColor = FocusFill,
            focusedContentColor = FocusText,
            pressedContainerColor = FocusFill,
            pressedContentColor = FocusText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color(0x55FFFFFF), 10.dp)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Carte image (affiche de film, vignette d'épisode, logo de chaîne) qui se soulève au focus.
 * Le titre sous l'image passe en blanc quand la carte a le focus.
 */
@Composable
fun MediaCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f,
    subtitle: String? = null,
    progress: Float = 0f,
    contentScale: ContentScale = ContentScale.Crop,
    imagePadding: Dp = 0.dp,
    focusRequester: FocusRequester? = null,
    /** Appui long sur OK (ajout/retrait des favoris). */
    onLongClick: (() -> Unit)? = null,
    favorite: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Card,
                focusedContainerColor = Card,
                pressedContainerColor = Card,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color(0x80000000), 14.dp)),
        ) {
            // Titre en fond : visible si l'image est absente ou ne charge pas
            Text(
                title,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize().padding(imagePadding),
                )
            }
            if (favorite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(Color(0xCC000000), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("★", color = Color(0xFFFFD60A), fontSize = 11.sp)
                }
            }
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height(3.dp)
                        .background(Color(0x66FFFFFF), CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                title,
                color = if (focused) TextPrimary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Ligne de métadonnées séparées par des points : « 2023 · Drame · 2 h 05 · ★ 7,4 ». */
@Composable
fun MetaLine(parts: List<String?>, modifier: Modifier = Modifier) {
    val text = parts.filterNot { it.isNullOrBlank() }.joinToString("  ·  ")
    if (text.isNotEmpty()) {
        Text(text, modifier = modifier, color = Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CenteredMessage(text: String, color: Color = TextSecondary, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, color = color, fontSize = 15.sp, textAlign = TextAlign.Center)
            action?.invoke()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

/** Espacement horizontal standard des écrans (zone de sécurité TV). */
val ScreenPadding = PaddingValues(horizontal = 48.dp)

/** « 01:45:00 », « 105 min » ou « 6300 » (secondes) → « 1 h 45 ». */
fun formatDuration(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val minutes: Int = when {
        raw.contains(":") -> {
            val p = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
            when (p.size) {
                3 -> p[0] * 60 + p[1]
                2 -> p[0]
                else -> return raw
            }
        }
        raw.trim().endsWith("min") -> raw.filter { it.isDigit() }.toIntOrNull() ?: return raw
        else -> raw.trim().toIntOrNull()?.let { if (it > 600) it / 60 else it } ?: return raw
    }
    if (minutes <= 0) return null
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}

fun formatRating(raw: String?): String? =
    raw?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ " + "%.1f".format(it).replace('.', ',') }


/** Champ de saisie adapté à la télécommande : OK ouvre le clavier, le cadre devient blanc au focus. */
@Composable
fun TvTextField(
    label: String?,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = false,
                // À la télécommande, le simple passage du focus ne doit pas ouvrir le clavier
                showKeyboardOnFocus = false,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                // OK sur le champ : ouvre le clavier
                .onPreviewKeyEvent { ev ->
                    val isOk = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                    if (isOk && ev.type == KeyEventType.KeyUp) keyboard?.show()
                    isOk
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) keyboard?.hide()
                }
                .background(if (focused) Color(0x40FFFFFF) else Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(hint, color = Color(0x66FFFFFF), fontSize = 15.sp)
                    inner()
                }
            },
        )
    }
}

/** Rangée horizontale titrée (accueil, recherche). */
@Composable
fun Shelf(title: String, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 48.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}
