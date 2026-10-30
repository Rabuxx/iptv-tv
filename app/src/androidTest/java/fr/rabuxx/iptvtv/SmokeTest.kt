package fr.rabuxx.iptvtv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests d'interface lancés par la CI sur un émulateur : l'app démarre sans planter
 * et affiche l'écran de connexion (installation neuve, donc sans identifiants).
 * L'émulateur de la CI est un téléphone en portrait : l'écran, conçu pour la TV, y déborde.
 * On vérifie donc que les éléments existent, sans exiger qu'ils soient dans la zone visible.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun ecranDeConnexionAuPremierLancement() {
        rule.onNodeWithText("Rabux TV").assertExists()
        rule.onNodeWithText("Se connecter").assertExists()
        rule.onNodeWithText("Adresse du serveur").assertExists()
    }

    @Test
    fun connexionDepuisLeTelephoneProposee() {
        rule.onNodeWithText("Ou depuis ton téléphone").assertExists()
    }
}
