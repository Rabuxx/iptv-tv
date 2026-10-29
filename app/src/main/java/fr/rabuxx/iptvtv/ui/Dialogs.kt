package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.rabuxx.iptvtv.AppDialog
import fr.rabuxx.iptvtv.MainViewModel
import fr.rabuxx.iptvtv.PinPurpose
import fr.rabuxx.iptvtv.data.PhoneLinkMode

/**
 * Fenêtres par-dessus l'écran courant. Une vraie fenêtre Dialog garde le focus de la télécommande
 * à l'intérieur tant qu'elle est ouverte ; Retour la ferme.
 */
@Composable
fun AppDialogHost(dialog: AppDialog?, vm: MainViewModel) {
    if (dialog == null) return
    Dialog(
        onDismissRequest = vm::dismissDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        when (dialog) {
            is AppDialog.CategoryOptions -> CategoryOptionsDialog(dialog, vm)
            is AppDialog.Pin -> PinDialog(dialog, vm)
            is AppDialog.WhatsNew -> WhatsNewDialog(dialog, vm)
            is AppDialog.ReminderDue -> ReminderDialog(dialog, vm)
            is AppDialog.PhoneEntry -> PhoneEntryDialog(dialog, vm)
        }
    }
}

@Composable
private fun DialogCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(420.dp)
            .background(Color(0xF21C1C1E), RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        content()
    }
}

/** Demande le focus sur le premier bouton une fois la fenêtre affichée. */
@Composable
private fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return requester
}

// ---------- Options d'une catégorie (appui long sur une pastille) ----------

@Composable
private fun CategoryOptionsDialog(d: AppDialog.CategoryOptions, vm: MainViewModel) {
    val first = rememberInitialFocus()
    DialogCard(
        title = d.category.name,
        subtitle = "Les catégories masquées se réaffichent dans Réglages › Contrôle parental.",
    ) {
        ActionButton(
            "Masquer cette catégorie",
            onClick = { vm.hideCategory(d.section, d.category.id) },
            modifier = Modifier.fillMaxWidth().focusRequester(first),
        )
        if (d.locked) {
            ActionButton(
                "Ne plus verrouiller",
                onClick = { vm.requestPin(PinPurpose.RemoveLock(d.section, d.category.id)) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ActionButton(
                "Verrouiller avec un code",
                onClick = { vm.lockCategory(d.section, d.category.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ActionButton("Annuler", onClick = vm::dismissDialog, modifier = Modifier.fillMaxWidth())
    }
}

// ---------- Code parental ----------

private const val PIN_LENGTH = 4

@Composable
private fun PinDialog(d: AppDialog.Pin, vm: MainViewModel) {
    val creating = d.purpose is PinPurpose.Create
    var digits by remember(d) { mutableStateOf("") }
    // Création : le code est saisi deux fois
    var firstEntry by remember(d) { mutableStateOf<String?>(null) }
    var localError by remember(d) { mutableStateOf<String?>(null) }

    val title = when (d.purpose) {
        is PinPurpose.Create -> if (firstEntry == null) "Choisis un code à 4 chiffres" else "Confirme le code"
        is PinPurpose.Unlock -> "Catégorie verrouillée"
        is PinPurpose.RemoveLock -> "Déverrouiller la catégorie"
        PinPurpose.RemovePin -> "Supprimer le code parental"
        PinPurpose.ResetCategories -> "Réafficher toutes les catégories"
    }

    fun add(digit: Char) {
        if (digits.length >= PIN_LENGTH) return
        localError = null
        digits += digit
        if (digits.length < PIN_LENGTH) return
        val entered = digits
        digits = ""
        when {
            !creating -> vm.submitPin(d.purpose, entered)
            firstEntry == null -> firstEntry = entered
            firstEntry == entered -> vm.submitPin(d.purpose, entered)
            else -> {
                firstEntry = null
                localError = "Les deux codes sont différents, recommence"
            }
        }
    }

    fun erase() {
        digits = digits.dropLast(1)
    }

    val first = rememberInitialFocus()

    Box(
        // Les touches numériques de la télécommande fonctionnent aussi
        Modifier.onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val digit = digitOf(ev.key)
            when {
                digit != null -> { add(digit); true }
                ev.key == Key.Backspace || ev.key == Key.Delete -> { erase(); true }
                else -> false
            }
        },
    ) {
        DialogCard(title = title, subtitle = d.error ?: localError) {
            // Cases du code
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                repeat(PIN_LENGTH) { i ->
                    Box(
                        Modifier
                            .size(18.dp)
                            .border(2.dp, Color.White, CircleShape)
                            .then(if (i < digits.length) Modifier.background(Color.White, CircleShape) else Modifier),
                    )
                }
            }
            // Pavé numérique
            val rows = listOf("123", "456", "789")
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { c, ch ->
                        KeypadKey(
                            label = ch.toString(),
                            onClick = { add(ch) },
                            modifier = if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeypadKey("✕", onClick = vm::dismissDialog)
                KeypadKey("0", onClick = { add('0') })
                KeypadKey("⌫", onClick = { erase() })
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 72.dp, height = 48.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            contentColor = TextPrimary,
            focusedContainerColor = FocusFill,
            focusedContentColor = FocusText,
            pressedContainerColor = FocusFill,
            pressedContentColor = FocusText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Text(label, modifier = Modifier.align(Alignment.Center), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Touche numérique de la télécommande ou du pavé numérique, ou null. */
fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

// ---------- Quoi de neuf ----------

@Composable
private fun WhatsNewDialog(d: AppDialog.WhatsNew, vm: MainViewModel) {
    val first = rememberInitialFocus()
    DialogCard(title = "Nouveautés de la ${d.version}") {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            d.notes.lines()
                .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotEmpty() && !it.startsWith("**Full Changelog") }
                .forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = TextSecondary, fontSize = 14.sp)
                        Text(line, fontSize = 14.sp)
                    }
                }
        }
        ActionButton("OK", onClick = vm::dismissDialog, modifier = Modifier.fillMaxWidth().focusRequester(first))
    }
}

// ---------- Rappel : le programme commence ----------

@Composable
private fun ReminderDialog(d: AppDialog.ReminderDue, vm: MainViewModel) {
    val first = rememberInitialFocus()
    val start = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(java.util.Date(d.reminder.startTs * 1000))
    DialogCard(
        title = d.reminder.title,
        subtitle = "Commence à $start sur ${d.reminder.channel.name}",
    ) {
        ActionButton(
            "▶  Regarder maintenant",
            onClick = { vm.watchReminder(d.reminder) },
            modifier = Modifier.fillMaxWidth().focusRequester(first),
        )
        ActionButton("Plus tard", onClick = vm::dismissDialog, modifier = Modifier.fillMaxWidth())
    }
}

// ---------- Saisie depuis le téléphone (clé TMDB) ----------

@Composable
private fun PhoneEntryDialog(d: AppDialog.PhoneEntry, vm: MainViewModel) {
    val url = rememberPhoneLinkUrl(d.mode) { form -> vm.onPhoneForm(d.mode, form) }
    val first = rememberInitialFocus()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PhoneLinkCard(
            url = url,
            title = when (d.mode) {
                PhoneLinkMode.Tmdb -> "Clé TMDB depuis ton téléphone"
                PhoneLinkMode.Login -> "Connexion depuis ton téléphone"
            },
            modifier = Modifier.width(380.dp),
        )
        if (d.mode == PhoneLinkMode.Tmdb) {
            Text(
                "Clé gratuite : themoviedb.org › Paramètres › API",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        ActionButton("Fermer", onClick = vm::dismissDialog, modifier = Modifier.focusRequester(first))
    }
}
