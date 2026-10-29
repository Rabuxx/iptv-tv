package fr.rabuxx.iptvtv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests d'interface lancés par la CI sur un émulateur : l'app démarre sans planter
 * et affiche l'écran de connexion (installation neuve, donc sans identifiants).
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun ecranDeConnexionAuPremierLancement() {
        rule.onNodeWithText("Rabux TV").assertIsDisplayed()
        rule.onNodeWithText("Se connecter").assertIsDisplayed()
        rule.onNodeWithText("Adresse du serveur").assertIsDisplayed()
    }

    @Test
    fun connexionDepuisLeTelephoneProposee() {
        rule.onNodeWithText("Ou depuis ton téléphone").assertIsDisplayed()
    }
}
