package app.suical.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.suical.llm.LlmBridge
import java.time.LocalDate

private val LLM_APPS = listOf(
    "com.anthropic.claude" to "Claude",
    "com.openai.chatgpt" to "ChatGPT",
    "com.google.android.apps.bard" to "Gemini",
    "" to "Me demander à chaque fois",
)

@Composable
fun SettingsScreen(x: Ctx, vm: MainViewModel) {
    val context = LocalContext.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var pasting by rememberSaveable { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
        }.onSuccess { vm.say("Fichier enregistré") }.onFailure { vm.say("Écriture impossible") }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text == null) vm.say("Lecture impossible") else vm.importJson(text) { vm.say(it ?: "Import terminé") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("App IA")
        Hint("Les boutons « Demander à l'IA » ouvrent cette app avec la demande déjà écrite.")
        Choice(LLM_APPS, x.s.llmPkg.takeIf { p -> LLM_APPS.any { it.first == p } } ?: "com.anthropic.claude") { vm.setLlmPkg(it) }

        PlanSettings(x, vm)

        SectionTitle("Sauvegarde")
        Hint("Tout est stocké sur ce téléphone. Exporte de temps en temps. Le format est le même que celui du site : un export du site s'importe ici.")
        PrimaryButton("Enregistrer dans un fichier") { save.launch("suical-${LocalDate.now()}.json") }
        GhostButton("Copier dans le presse-papier") { LlmBridge.copy(context, vm.exportJson()); vm.say("Copié dans le presse-papier") }
        GhostButton("Importer un fichier") { load.launch(arrayOf("*/*")) }
        GhostButton("Importer depuis le presse-papier") { pasting = true }
        DangerButton("Réinitialiser") { confirmReset = true }
    }

    if (confirmReset) Confirm("Tout réinitialiser ?", "Stock, plats et aliments repartent des valeurs de départ.", "Réinitialiser",
        { confirmReset = false }) { vm.resetAll() }
    if (pasting) Confirm("Importer le presse-papier ?", "Toutes tes données seront remplacées par cet export.", "Importer",
        { pasting = false }) { vm.importJson(LlmBridge.paste(context)) { vm.say(it ?: "Import terminé") } }
}
