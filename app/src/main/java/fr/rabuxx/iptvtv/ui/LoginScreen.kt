package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import fr.rabuxx.iptvtv.data.Credentials
import fr.rabuxx.iptvtv.data.PhoneLinkMode

@Composable
fun LoginScreen(
    initial: Credentials?,
    loading: Boolean,
    error: String?,
    onSubmit: (Credentials) -> Unit,
    onPhoneForm: (Map<String, String>) -> Unit,
) {
    var server by rememberSaveable { mutableStateOf(initial?.server ?: "") }
    var username by rememberSaveable { mutableStateOf(initial?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(initial?.password ?: "") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submit = { onSubmit(Credentials(server, username, password)) }

    // Pendant la connexion (y compris la reconnexion automatique au lancement), pas de formulaire :
    // aucun champ ne peut prendre le focus et ouvrir le clavier
    if (loading) {
        CenteredMessage("Connexion à ton abonnement…")
        return
    }

    // Saisie depuis le téléphone : les champs se remplissent et la connexion part toute seule
    val phoneUrl = rememberPhoneLinkUrl(PhoneLinkMode.Login) { form ->
        server = form["server"].orEmpty()
        username = form["username"].orEmpty()
        password = form["password"].orEmpty()
        onPhoneForm(form)
    }

    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .background(Glass, RoundedCornerShape(28.dp))
                .padding(horizontal = 36.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Rabux TV", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Connecte ton abonnement Xtream Codes",
                fontSize = 14.sp,
                color = TextSecondary,
            )
            TvTextField(
                label = "Adresse du serveur",
                hint = "http://exemple.com:8080",
                value = server,
                onValueChange = { server = it },
                keyboardType = KeyboardType.Uri,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            )
            TvTextField(
                label = "Identifiant",
                hint = "username",
                value = username,
                onValueChange = { username = it },
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            )
            // Mot de passe + bouton pour l'afficher en clair (utile pour vérifier une saisie à la télécommande)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvTextField(
                    label = "Mot de passe",
                    hint = "password",
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.weight(1f),
                    isPassword = !showPassword,
                    imeAction = ImeAction.Done,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                )
                Box(Modifier.padding(bottom = 4.dp)) {
                    ChipButton(
                        label = if (showPassword) "Masquer" else "Afficher",
                        selected = showPassword,
                        onClick = { showPassword = !showPassword },
                    )
                }
            }
            if (error != null) {
                Text(error, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            ActionButton(
                label = if (loading) "Connexion…" else "Se connecter",
                onClick = { if (!loading) submit() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PhoneLinkCard(url = phoneUrl, title = "Ou depuis ton téléphone", modifier = Modifier.width(300.dp))
    }
}
