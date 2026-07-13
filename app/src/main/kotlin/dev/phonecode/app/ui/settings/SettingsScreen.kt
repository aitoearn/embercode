package dev.phonecode.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.phonecode.app.agent.ChatViewModel
import dev.phonecode.app.R
import dev.phonecode.app.data.CustomModel
import dev.phonecode.app.data.CustomProvider
import dev.phonecode.app.data.ManagedSkill
import dev.phonecode.app.data.SkillScope
import dev.phonecode.app.data.SkillStatus
import dev.phonecode.app.data.ThemeMode
import dev.phonecode.app.ui.SettingsViewModel
import dev.phonecode.app.ui.chat.MarkdownBlocks
import dev.phonecode.app.ui.components.PcButton
import dev.phonecode.app.ui.components.PcField
import dev.phonecode.app.ui.components.PcGroup
import dev.phonecode.app.ui.components.PcIconButton
import dev.phonecode.app.ui.components.PcRow
import dev.phonecode.app.ui.components.PcSectionLabel
import dev.phonecode.app.ui.components.PcToggle
import dev.phonecode.app.ui.components.contentVerticalScroll
import dev.phonecode.app.ui.components.predictiveBackTransform
import dev.phonecode.app.ui.components.rememberPredictiveBackMotion
import dev.phonecode.app.ui.theme.PcMono
import dev.phonecode.app.ui.theme.PhoneEasings
import dev.phonecode.app.ui.theme.PhoneSprings
import dev.phonecode.app.ui.theme.Spacing
import dev.phonecode.agent.AgentMode
import dev.phonecode.tools.mcp.McpServerConfig
import dev.phonecode.tools.mcp.McpServerSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The page each settings route pops back to - doc pages return to About, provider details to
 *  Providers. The old "always home" pop sent the back GESTURE somewhere different from the back
 *  BUTTON on nested pages (round-4: "sometimes it brings back to settings... sometimes the About"). */
private fun parentOf(page: String): String = when {
    page.startsWith("doc:") -> "about"
    page.startsWith("provider:") -> "providers"
    else -> "home"
}

/** Nav depth per route - the slide direction keys off depth, so popping doc → about animates as a
 *  POP even though neither side is "home" (the old home-test played nested pops backwards). */
private fun depthOf(page: String): Int = when {
    page == "home" -> 0
    page.startsWith("doc:") || page.startsWith("provider:") -> 2
    else -> 1
}

/** Settings: a home list + every sub-page, navigated with an iOS-style slide.
 *  [initialPage] lets callers (onboarding) deep-link straight to a sub-page. */
@Composable
fun SettingsScreen(vm: ChatViewModel, settingsVm: SettingsViewModel, onBack: () -> Unit, initialPage: String = "home") {
    var page by rememberSaveable(initialPage) { mutableStateOf(initialPage) }
    var predictiveCommit by remember { mutableStateOf(false) }
    val navigateBack = {
        if (page == initialPage) onBack() else page = parentOf(page)
    }

    val backMotion = rememberPredictiveBackMotion(enabled = page != initialPage) {
        predictiveCommit = true
        navigateBack()
    }
    LaunchedEffect(page) { predictiveCommit = false }

    Box(Modifier.fillMaxSize()) {
        if (backMotion.progress > 0f) {
            Box(Modifier.fillMaxSize()) {
                SettingsPageContent(parentOf(page), vm, settingsVm, onBack = {}, navigate = {})
            }
        }
        Box(Modifier.fillMaxSize().predictiveBackTransform(backMotion)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (predictiveCommit) {
                        androidx.compose.animation.EnterTransition.None togetherWith
                            androidx.compose.animation.ExitTransition.None
                    } else {
                        val pop = depthOf(targetState) < depthOf(initialState)
                        (if (pop) {
                            (slideInHorizontally(tween(260, easing = PhoneEasings.iOSStandard)) { -it / 4 }) togetherWith
                                slideOutHorizontally(tween(220, easing = PhoneEasings.iOSStandard)) { it }
                        } else {
                            (slideInHorizontally(tween(260, easing = PhoneEasings.iOSStandard)) { it }) togetherWith
                                slideOutHorizontally(tween(220, easing = PhoneEasings.iOSStandard)) { -it / 4 }
                        }).apply { targetContentZIndex = if (pop) -1f else 1f }
                    }
                },
                label = "settings",
            ) { p ->
                Box(Modifier.fillMaxSize()) {
                    SettingsPageContent(p, vm, settingsVm, navigateBack) { page = it }
                }
            }
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: String,
    vm: ChatViewModel,
    settingsVm: SettingsViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    when (page) {
        "general" -> GeneralPage(settingsVm, onBack)
        "appearance" -> AppearancePage(settingsVm, onBack)
        "personal" -> PersonalPage(settingsVm, onBack)
        "providers" -> ProvidersPage(vm, onOpenProvider = { navigate("provider:$it") }, onBack = onBack)
        "mcp" -> McpPage(vm, onBack)
        "skills" -> SkillsPage(vm, onBack)
        "files" -> FilesPage(vm, settingsVm, onBack)
        "git" -> GitPage(vm, settingsVm, onBack)
        "export" -> ExportPage(vm, settingsVm, onBack)
        "about" -> AboutPage(vm, onOpenDoc = navigate, onBack = onBack)
        "doc:terms" -> DocPage("Terms of Service", "terms.md", onBack)
        "doc:privacy" -> DocPage("Privacy Policy", "privacy.md", onBack)
        "doc:licenses" -> DocPage("Open-source notices", "licenses.md", onBack)
        else -> {
            if (page.startsWith("provider:")) {
                ProviderDetailPage(vm, page.removePrefix("provider:"), onBack)
            } else {
                HomePage(vm, settingsVm, onBack = onBack, onOpen = navigate)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Scaffolding
// ---------------------------------------------------------------------------------------------

@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }
    Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Column(
            Modifier.fillMaxSize()
                .contentVerticalScroll(scrollState)
                .background(colors.background)
                .padding(start = Spacing.m, end = Spacing.m, top = Spacing.navBarHeight + 4.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            content()
            Spacer(Modifier.height(Spacing.xxl))
        }
        Row(
            Modifier.fillMaxWidth().height(Spacing.navBarHeight)
                .shadow(if (scrolled) 2.dp else 0.dp, RectangleShape, clip = false)
                .background(colors.background)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PcIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.touchTarget))
        }
    }
}

// M3 Expressive list metrics (round-3 settings redesign): bodyLarge headline + bodyMedium
// supporting text, 24dp leading icon in onSurfaceVariant, trailing chevron - no hairlines,
// the connected-card gaps do the separating.

@Composable
private fun NavRow(label: String, value: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PcRow(onClick = onClick) {
        if (icon != null) Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToggleRow(label: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    PcRow {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
        }
        PcToggle(checked, onChange, "$label ${if (checked) "on" else "off"}")
    }
}

@Composable
private fun CheckRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PcRow(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.onBackground, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Note(text: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().padding(top = Spacing.xs).clip(MaterialTheme.shapes.medium)
            .background(colors.surface).padding(14.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = colors.secondary) }
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomePage(vm: ChatViewModel, settingsVm: SettingsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    Page("Settings", onBack) {
        // The first group carries no label - an unlabeled lead group is the platform convention
        // (Grok's settings open straight into the Customize cards).
        PcGroup {
            NavRow("General", icon = Icons.Outlined.Tune) { onOpen("general") }
            NavRow("Appearance", value = settings.mode.name.lowercase().replaceFirstChar { it.uppercase() }, icon = Icons.Outlined.Palette) { onOpen("appearance") }
            NavRow("Personalization", icon = Icons.Outlined.Person) { onOpen("personal") }
        }
        PcSectionLabel("Models")
        PcGroup {
            NavRow("Providers", icon = Icons.Outlined.Cloud) { onOpen("providers") }
        }
        PcSectionLabel("Tools")
        PcGroup {
            NavRow("MCP servers", value = "${state.mcpServers.size}", icon = Icons.Outlined.Extension) { onOpen("mcp") }
            NavRow(
                "Skills",
                value = state.skills.count { it.status == SkillStatus.ACTIVE }.toString(),
                icon = Icons.Outlined.AutoAwesome,
            ) { onOpen("skills") }
        }
        // "GIT > Git" was the same duplication as the old GENERAL group - the workspace label
        // says what the section governs (per-project repos), the row keeps the familiar name.
        PcSectionLabel("Workspace")
        PcGroup {
            NavRow(
                "Files & permissions",
                value = if (state.sharedFolders.isEmpty()) "Private" else "${state.sharedFolders.size} linked",
                icon = Icons.Outlined.Folder,
            ) { onOpen("files") }
            NavRow("Git", icon = Icons.Outlined.AccountTree) { onOpen("git") }
        }
        PcSectionLabel("Data")
        PcGroup {
            NavRow("Export & import", icon = Icons.Outlined.SwapVert) { onOpen("export") }
        }
        Spacer(Modifier.height(8.dp))
        PcGroup {
            NavRow("About", icon = Icons.Outlined.Info) { onOpen("about") }
        }
    }
}

@Composable
private fun GeneralPage(settingsVm: SettingsViewModel, onBack: () -> Unit) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    Page("General", onBack) {
        PcSectionLabel("Defaults")
        PcGroup {
            AgentMode.entries.forEach { mode ->
                CheckRow(
                    "Default mode: " + mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = settings.defaultMode == mode.name,
                ) {
                    // Default governs NEW conversations (applied in ChatViewModel.newChat / init); changing
                    // it must not retroactively flip the active chat's mode - that's the per-chat Plan toggle.
                    settingsVm.update { it.copy(defaultMode = mode.name) }
                }
            }
            ToggleRow("Send on Enter", checked = settings.sendOnEnter) { v -> settingsVm.update { it.copy(sendOnEnter = v) } }
        }
    }
}

@Composable
private fun FilesPage(vm: ChatViewModel, settingsVm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.linkSharedFolder(uri)
    }
    Page("Files & permissions", onBack) {
        PcSectionLabel("Workspace")
        PcGroup {
            PcRow {
                Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Private project workspace", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text("Permanent and fully available to the agent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            }
        }
        PcSectionLabel("Phone folders")
        if (state.sharedFolders.isNotEmpty()) {
            PcGroup {
                state.sharedFolders.forEach { folder ->
                    PcRow {
                        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                            Text(if (folder.writable) "Read & write" else "Read only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PcIconButton(Icons.Filled.Delete, "Remove ${folder.name}") { vm.unlinkSharedFolder(folder.id) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PcButton("Link a folder", filled = false) { picker.launch(null) }
        Note("The system picker grants access only to the folder you choose. Linked access survives app restarts and can be removed here or in system settings.")
        PcSectionLabel("Agent changes")
        PcGroup {
            CheckRow("Ask before changes", selected = !settings.autoAccept) {
                settingsVm.update { it.copy(autoAccept = false) }
                vm.setAutoAccept(false)
            }
            CheckRow("Allow changes automatically", selected = settings.autoAccept) {
                settingsVm.update { it.copy(autoAccept = true) }
                vm.setAutoAccept(true)
            }
        }
        Note("Reading the active workspace and linked folders is always allowed. This setting controls writes, terminal commands, Git operations, and other actions that can change data.")
        state.notice?.let {
            Spacer(Modifier.height(10.dp))
            Note(it)
            LaunchedEffect(it) { delay(3000); vm.clearNotice() }
        }
        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Note(it)
            LaunchedEffect(it) { delay(5000); vm.clearError() }
        }
    }
}

@Composable
private fun AppearancePage(settingsVm: SettingsViewModel, onBack: () -> Unit) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    Page("Appearance", onBack) {
        PcSectionLabel("Theme")
        PcGroup {
            ThemeMode.entries.forEach { mode ->
                CheckRow(
                    mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = settings.mode == mode,
                ) { settingsVm.update { it.copy(themeMode = mode.name) } }
            }
        }
    }
}

@Composable
private fun PersonalPage(settingsVm: SettingsViewModel, onBack: () -> Unit) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var text by remember(settings.customInstructions) { mutableStateOf(settings.customInstructions) }
    // Persist on a debounce instead of per keystroke (the old onValueChange rewrote the whole settings
    // file on every character). The debounce coalesces typing; the onDispose flush below covers leaving
    // the page within the debounce window so no edit is lost.
    LaunchedEffect(text) {
        if (text != settings.customInstructions) {
            delay(400)
            settingsVm.update { it.copy(customInstructions = text) }
        }
    }
    val latest by rememberUpdatedState(text)
    DisposableEffect(Unit) {
        onDispose { settingsVm.update { if (latest != it.customInstructions) it.copy(customInstructions = latest) else it } }
    }
    Page("Personalization", onBack) {
        PcSectionLabel("Custom instructions")
        PcField(
            text,
            onValueChange = { text = it },
            placeholder = "Tell the agent how you like to work - style, tools, conventions...",
            singleLine = false,
            minLines = 5,
        )
    }
}

@Composable
private fun ProvidersPage(vm: ChatViewModel, onOpenProvider: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var addingCustom by remember { mutableStateOf(false) }
    Page("Providers", onBack) {
        if (!state.codexConnected) {
            PcSectionLabel("ChatGPT")
            PcButton("Sign in with ChatGPT (Codex)") {
                vm.startCodexSignIn()?.let { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        PcSectionLabel("Providers")
        if (vm.secureStorageUnavailable()) {
            Text(
                "Secure storage is unavailable on this device. PhoneCode will not save API keys or sign-in credentials.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
        // Hoist the provider list and the key lookup out of per-recomposition work: keyFor() decrypts from
        // EncryptedSharedPreferences, so doing it per row per frame ran crypto on every toggle/recompose.
        // Keyed on state.models (changes when custom providers reload); a fresh entry after editing a key
        // on the detail page resets this remember because the page leaves the AnimatedContent composition.
        val providers = remember(state.models, state.codexConnected) {
            vm.allProviders().filter { it.id != "codex" || state.codexConnected }
        }
        val keyedIds = remember(state.models) { providers.filter { vm.keyFor(it.id).isNotBlank() }.map { it.id }.toSet() }
        PcGroup {
            providers.forEach { preset ->
                val enabled = preset.id !in state.disabledProviders
                val connected = preset.id == "codex" && state.codexConnected
                val hasKey = connected || preset.id in keyedIds
                PcRow(onClick = { onOpenProvider(preset.id) }) {
                    Column(Modifier.weight(1f)) {
                        Text(preset.displayName, style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.onBackground else colors.tertiary)
                        Text(
                            if (connected) "Signed in with ChatGPT" else if (hasKey) "Key set" else "No key",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasKey) colors.onSurfaceVariant else colors.tertiary,
                        )
                    }
                    PcToggle(enabled, { vm.toggleProviderDisabled(preset.id) }, "${preset.displayName} enabled")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
                }
            }
        }
        // Custom providers (round-3: "Add the option to add a custom provider") - any
        // OpenAI-compatible or Anthropic-style endpoint, stored in providers.json (the same
        // file the agent can edit), so both paths land in one catalog.
        Spacer(Modifier.height(Spacing.s))
        PcButton("Add custom provider", filled = false, icon = Icons.Filled.Add) { addingCustom = true }
    }
    if (addingCustom) {
        CustomProviderDialog(
            existingIds = vm.allProviders().map { it.id }.toSet(),
            onSave = { id, provider -> vm.saveCustomProvider(id, provider) },
            onDismiss = { addingCustom = false },
        )
    }
}

@Composable
private fun ProviderDetailPage(vm: ChatViewModel, providerId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val preset = vm.allProviders().firstOrNull { it.id == providerId }
    var key by remember(providerId) { mutableStateOf(vm.keyFor(providerId)) }
    Page(preset?.displayName ?: providerId, onBack) {
        if (providerId == "codex") {
            PcSectionLabel("Account")
            PcGroup {
                PcRow(onClick = { vm.signOutCodex(); onBack() }) {
                    Column(Modifier.weight(1f)) {
                        Text("ChatGPT", style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                        Text("Signed in", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Text("Disconnect", style = MaterialTheme.typography.labelLarge, color = colors.error)
                }
            }
        } else {
            PcSectionLabel("API key")
            PcField(key, { key = it; vm.setKey(providerId, it) }, "API key", password = true)
        }
        val models = state.models.filter { it.providerId == providerId }
        PcSectionLabel("Models · ${models.size}")
        if (models.isEmpty()) {
            Note("No models loaded for this provider yet. Models refresh automatically when PhoneCode opens.")
        } else {
            // Search + bulk visibility (device feedback): long provider lists need both.
            var modelQuery by remember(providerId) { mutableStateOf("") }
            PcField(modelQuery, { modelQuery = it }, "Search models")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PcButton("All on", filled = false, modifier = Modifier.weight(1f)) { vm.setAllModelsHidden(models, hidden = false) }
                PcButton("All off", filled = false, modifier = Modifier.weight(1f)) { vm.setAllModelsHidden(models, hidden = true) }
            }
            Spacer(Modifier.height(8.dp))
            val shown = models.filter { modelQuery.isBlank() || it.label.contains(modelQuery, ignoreCase = true) || it.modelId.contains(modelQuery, ignoreCase = true) }
            if (shown.isEmpty()) Note("No models match \"$modelQuery\".")
            PcGroup {
                shown.forEach { option ->
                    val k = "${option.providerId}/${option.modelId}"
                    val visible = k !in state.hiddenModels
                    PcRow {
                        Text(
                            option.label.substringAfterLast(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (visible) colors.onBackground else colors.tertiary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        PcToggle(visible, { vm.toggleModelHidden(option) }, "${option.label} visible")
                    }
                }
            }
        }
        if (vm.isCustomProvider(providerId)) {
            PcSectionLabel("Custom provider")
            PcGroup {
                PcRow(onClick = { vm.deleteCustomProvider(providerId); onBack() }) {
                    Text("Remove this provider", style = MaterialTheme.typography.bodyLarge, color = colors.error)
                }
            }
        }
    }
}

@Composable
private fun McpPage(vm: ChatViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var editorDirty by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val closeEditor = {
        if (editorDirty) confirmDiscard = true else editing = null
    }
    val detailBackMotion = rememberPredictiveBackMotion(enabled = editing != null && !confirmDiscard) { closeEditor() }
    val visible = remember(state.mcpServers, query) {
        state.mcpServers.filter { (name, server) ->
            query.isBlank() || name.contains(query, true) || server.url.contains(query, true)
        }
    }
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxSize().then(
            if (editing != null) Modifier.clearAndSetSemantics {} else Modifier,
        ),
    ) {
    Page("MCP servers", onBack) {
        val connected = state.mcpSnapshots.count { it.value.connected }
        Note("$connected connected · ${state.mcpServers.count { it.value.enabled }} enabled · ${state.mcpToolCount} tools")
        state.mcpConfigError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error)
            Note("The existing opencode.json has been preserved. Fix it before changing MCP servers here.")
        }
        if (state.mcpServers.isNotEmpty()) PcField(query, { query = it }, "Search servers")
        PcSectionLabel("Servers")
        if (state.mcpServers.isEmpty()) {
            Note("No MCP servers configured. Add one to give the agent extra tools over HTTP.")
        } else {
            PcGroup {
                visible.entries.forEach { (name, server) ->
                    val snapshot = state.mcpSnapshots[name]
                    val status = when {
                        !server.enabled -> "Off"
                        name in state.mcpConnecting -> "Connecting"
                        snapshot?.connected == true -> "Connected · ${snapshot.tools.size} tools"
                        snapshot?.error?.isNotBlank() == true -> "Failed · ${snapshot.error}"
                        else -> "Not tested"
                    }
                    PcRow(onClick = if (state.mcpConfigError == null) ({ editorDirty = false; editing = name }) else null) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                            Text(
                                status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (snapshot?.connected == true) colors.primary else colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (state.mcpConfigError == null) PcToggle(server.enabled, { vm.setMcpEnabled(name, it) }, "$name enabled")
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (state.mcpConfigError == null) {
            Spacer(Modifier.height(Spacing.s))
            PcButton("Add server", filled = false, icon = Icons.Filled.Add) { editorDirty = false; editing = "" }
            if (state.mcpServers.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                PcButton("Reconnect enabled servers", filled = false) { vm.reconnectMcp() }
            }
        }
    }
    }
    editing?.let { initialName ->
        Box(Modifier.fillMaxSize().predictiveBackTransform(detailBackMotion).background(colors.background)) {
            McpServerPage(
                vm = vm,
                initialName = initialName,
                initial = state.mcpServers[initialName] ?: McpServerConfig(),
                existingNames = state.mcpServers.keys,
                snapshot = state.mcpSnapshots[initialName],
                onBack = closeEditor,
                onDirtyChange = { editorDirty = it },
                onSaved = { editorDirty = false; editing = null },
            )
        }
    }
    if (confirmDiscard) {
        ConfirmDiscardDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; editorDirty = false; editing = null },
        )
    }
    }
}

@Composable
private fun McpServerPage(
    vm: ChatViewModel,
    initialName: String,
    initial: McpServerConfig,
    existingNames: Set<String>,
    snapshot: McpServerSnapshot?,
    onBack: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSaved: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isNew = initialName.isEmpty()
    val scope = rememberCoroutineScope()
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialName, initial) { mutableStateOf(initial.url) }
    var headers by remember(initialName, initial) { mutableStateOf(initial.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    var timeout by remember(initialName, initial) { mutableStateOf(initial.timeout.toString()) }
    var enabled by remember(initialName, initial) { mutableStateOf(initial.enabled) }
    var error by remember(initialName, initial) { mutableStateOf<String?>(null) }
    var testing by remember(initialName) { mutableStateOf(false) }
    var saving by remember(initialName) { mutableStateOf(false) }
    var testResult by remember(initialName) { mutableStateOf<McpServerSnapshot?>(null) }
    var confirmDelete by remember(initialName) { mutableStateOf(false) }

    fun validationMessage(): String? {
        val finalName = if (isNew) name.trim() else initialName
        val finalTimeout = timeout.toLongOrNull()
        val invalidHeader = headers.lineSequence().filter { it.isNotBlank() }.firstOrNull { line ->
            val separator = line.indexOf(':')
            separator <= 0 || line.substring(0, separator).isBlank() || line.substring(separator + 1).isBlank()
        }
        return when {
            finalName.isBlank() -> "Name is required"
            url.isBlank() || !url.startsWith("https://") -> "Use an HTTPS MCP URL"
            isNew && finalName in existingNames -> "A server named $finalName already exists"
            invalidHeader != null -> "Each header must use Name: Value"
            finalTimeout == null || finalTimeout !in 1_000L..60_000L -> "Timeout must be between 1000 and 60000 ms"
            else -> null
        }
    }

    fun draft(): Pair<String, McpServerConfig>? {
        val finalName = if (isNew) name.trim() else initialName
        val finalTimeout = timeout.toLongOrNull()
        error = validationMessage()
        return if (error == null) {
            finalName to McpServerConfig("remote", url.trim(), parseHeaders(headers), enabled, finalTimeout!!)
        } else null
    }

    val initialHeaders = initial.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    val changed = if (isNew) {
        name.isNotBlank() || url.isNotBlank() || headers.isNotBlank() || timeout != initial.timeout.toString() || enabled != initial.enabled
    } else {
        url != initial.url || headers != initialHeaders || timeout != initial.timeout.toString() || enabled != initial.enabled
    }
    val canSubmit = validationMessage() == null && !testing && !saving
    LaunchedEffect(changed) { onDirtyChange(changed) }
    val shownSnapshot = testResult ?: snapshot.takeUnless { changed }
    Page(if (isNew) "Add MCP server" else initialName, onBack) {
        when {
            testing -> Note("Testing this configuration…")
            shownSnapshot?.connected == true -> Note("Connected to ${shownSnapshot.serverTitle.ifBlank { shownSnapshot.serverName }.ifBlank { name }}")
            shownSnapshot?.error?.isNotBlank() == true -> Text(shownSnapshot.error, style = MaterialTheme.typography.bodyMedium, color = colors.error)
            !isNew && !changed -> Note(if (enabled) "Not tested" else "Off")
        }
        PcSectionLabel("Connection")
        if (isNew) {
            McpFieldLabel("Server name")
            PcField(name, { name = it; error = null; testResult = null }, "context7", contentDescription = "Server name")
        }
        McpFieldLabel("Remote URL")
        PcField(url, { url = it; error = null; testResult = null }, "https://host/mcp", contentDescription = "Remote URL")
        McpFieldLabel("HTTP headers")
        PcField(
            headers,
            { headers = it; error = null; testResult = null },
            "Authorization: Bearer …",
            singleLine = false,
            minLines = 2,
            contentDescription = "HTTP headers",
        )
        Note("One Name: Value header per line. Values are stored in PhoneCode's private app data.")
        McpFieldLabel("Connection timeout")
        PcField(
            timeout,
            { timeout = it.filter(Char::isDigit); error = null; testResult = null },
            "5000 milliseconds",
            contentDescription = "Connection timeout in milliseconds",
        )
        Spacer(Modifier.height(Spacing.xs))
        ToggleRow("Enabled", checked = enabled) { enabled = it; error = null; testResult = null }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(top = Spacing.xs)) }
        Spacer(Modifier.height(Spacing.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.weight(1f)) {
                PcButton(if (testing) "Testing…" else "Test", filled = false, enabled = canSubmit) {
                    if (!testing) draft()?.let { (draftName, server) ->
                        scope.launch {
                            testing = true
                            testResult = vm.testMcpServer(draftName, server)
                            testing = false
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                PcButton(if (saving) "Saving…" else "Save", enabled = canSubmit) {
                    draft()?.let { (draftName, server) ->
                        scope.launch {
                            saving = true
                            vm.saveMcpServerAndWait(draftName, server).fold(
                                onSuccess = { onSaved() },
                                onFailure = { error = it.message ?: "MCP configuration could not be saved" },
                            )
                            saving = false
                        }
                    }
                }
            }
        }
        shownSnapshot?.takeIf { it.connected }?.let { connectedSnapshot ->
            PcSectionLabel("Server")
            PcGroup {
                McpValueRow("Name", connectedSnapshot.serverTitle.ifBlank { connectedSnapshot.serverName }.ifBlank { name })
                McpValueRow("Version", connectedSnapshot.serverVersion.ifBlank { "Unknown" })
                McpValueRow("Protocol", connectedSnapshot.protocolVersion)
                McpValueRow("Capabilities", connectedSnapshot.capabilities.sorted().joinToString().ifBlank { "None" })
            }
            if (connectedSnapshot.instructions.isNotBlank()) {
                PcSectionLabel("Instructions")
                Note(connectedSnapshot.instructions)
            }
            PcSectionLabel("Tools")
            if (connectedSnapshot.tools.isEmpty()) Note("This server exposes no tools.") else {
                PcGroup {
                    connectedSnapshot.tools.take(30).forEach { tool ->
                        PcRow {
                            Column {
                                Text(tool.title.ifBlank { tool.name }, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
                                if (tool.description.isNotBlank()) Text(tool.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (connectedSnapshot.tools.size > 30) Note("${connectedSnapshot.tools.size - 30} more tools")
            }
        }
        if (!isNew) {
            Spacer(Modifier.height(Spacing.l))
            PcButton(if (confirmDelete) "Confirm delete" else "Delete server", filled = false, destructive = true) {
                if (confirmDelete) {
                    vm.deleteMcpServer(initialName)
                    onSaved()
                } else {
                    confirmDelete = true
                }
            }
        }
    }
}

@Composable
private fun ConfirmDiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    Dialog(onDismissRequest = onKeepEditing) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(Spacing.m),
        ) {
            Text("Discard changes?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "This server has unsaved changes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.m),
            )
            PcButton("Keep editing", onClick = onKeepEditing)
            Spacer(Modifier.height(Spacing.xs))
            PcButton("Discard", filled = false, destructive = true, onClick = onDiscard)
        }
    }
}

@Composable
private fun McpFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun McpValueRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    PcRow {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private enum class SkillFilter { ALL, ACTIVE, OFF, ISSUES }

@Composable
private fun SkillsPage(vm: ChatViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SkillFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val selectedSkill = state.skills.firstOrNull { it.id == selectedId }
    val detailBackMotion = rememberPredictiveBackMotion(enabled = selectedSkill != null) { selectedId = null }
    val filtered = remember(state.skills, query, filter) {
        state.skills.filter { skill ->
            val matchesQuery = query.isBlank() || skill.name.contains(query, true) ||
                skill.manifest?.description?.contains(query, true) == true || skill.issue?.contains(query, true) == true
            val matchesFilter = when (filter) {
                SkillFilter.ALL -> true
                SkillFilter.ACTIVE -> skill.status == SkillStatus.ACTIVE
                SkillFilter.OFF -> skill.status == SkillStatus.DISABLED || skill.status == SkillStatus.SHADOWED
                SkillFilter.ISSUES -> skill.status == SkillStatus.INVALID
            }
            matchesQuery && matchesFilter
        }
    }
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxSize().then(
            if (selectedSkill != null) Modifier.clearAndSetSemantics {} else Modifier,
        ),
    ) {
    Page("Skills", onBack) {
        val active = state.skills.count { it.status == SkillStatus.ACTIVE }
        val issues = state.skills.count { it.status == SkillStatus.INVALID }
        Note("$active active · ${state.skills.size} discovered${if (issues > 0) " · $issues need attention" else ""}")
        PcField(query, { query = it }, "Search skills")
        SkillFilters(filter) { filter = it }
        PcSectionLabel(filter.label())
        if (filtered.isEmpty()) {
            Note("No matching skills.")
        } else {
            PcGroup {
                filtered.forEach { skill ->
                    PcRow(onClick = { selectedId = skill.id }) {
                        Column(Modifier.weight(1f)) {
                            Text(skill.name, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                            Text(
                                "${skill.scope.label()} · ${skill.status.label()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (skill.status == SkillStatus.INVALID) colors.error else colors.onSurfaceVariant,
                            )
                            skill.manifest?.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (skill.manifest != null && skill.status != SkillStatus.SHADOWED && skill.status != SkillStatus.INVALID) {
                            PcToggle(
                                skill.status == SkillStatus.ACTIVE,
                                { vm.setSkillEnabled(skill.id, it) },
                                "${skill.name} enabled",
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Note("Skill files reload automatically. The agent can create and edit global or project skills with your permission.")
    }
    }
    selectedSkill?.let { skill ->
        Box(Modifier.fillMaxSize().predictiveBackTransform(detailBackMotion).background(colors.background)) {
            SkillDetailPage(vm, skill) { selectedId = null }
        }
    }
    }
}

@Composable
private fun SkillDetailPage(vm: ChatViewModel, skill: ManagedSkill, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val manifest = skill.manifest
    var confirmDelete by remember(skill.id) { mutableStateOf(false) }
    Page(skill.name, onBack = onBack) {
        Note("${skill.scope.label()} · ${skill.status.label()}")
        skill.issue?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        manifest?.description?.takeIf { it.isNotBlank() }?.let { Note(it) }
        if (manifest != null && skill.status != SkillStatus.SHADOWED && skill.status != SkillStatus.INVALID) {
            ToggleRow(
                "Enabled",
                sub = "Applies immediately to the current agent session",
                checked = skill.status != SkillStatus.DISABLED,
            ) { vm.setSkillEnabled(skill.id, it) }
        } else if (skill.status == SkillStatus.SHADOWED) {
            Note("Another skill with this name takes precedence. Disable or edit the active copy to use this one.")
        }
        manifest?.body?.takeIf { it.isNotBlank() }?.let { instructions ->
            PcSectionLabel("Instructions")
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(colors.surface).padding(Spacing.m),
            ) {
                MarkdownBlocks(instructions)
            }
            Note("The agent can edit this skill with permission. Changes reload into this session automatically.")
        }
        PcSectionLabel("Details")
        PcGroup {
            if (!manifest?.compatibility.isNullOrBlank()) PcRow {
                Column {
                    Text("Compatibility", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Text(manifest.compatibility, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (!manifest?.license.isNullOrBlank()) PcRow {
                Text("License", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(manifest.license, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
            }
            if (skill.location.isNotBlank()) PcRow {
                Column {
                    Text("Location", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Text(skill.location, style = MaterialTheme.typography.bodySmall, color = colors.onBackground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))
        PcButton(if (confirmDelete) "Confirm delete" else "Delete skill", filled = false, destructive = true) {
            if (confirmDelete) {
                vm.deleteSkill(skill.id)
                onBack()
            } else {
                confirmDelete = true
            }
        }
    }
}

@Composable
private fun SkillFilters(selected: SkillFilter, onSelect: (SkillFilter) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SkillFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                Modifier.weight(1f).clip(MaterialTheme.shapes.large)
                    .background(if (active) colors.primary else colors.surfaceContainerHigh)
                    .semantics { this.selected = active; role = Role.Tab }
                    .clickable { onSelect(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(filter.shortLabel(), style = MaterialTheme.typography.labelMedium, color = if (active) colors.onPrimary else colors.onBackground)
            }
        }
    }
}

private fun SkillScope.label() = if (this == SkillScope.PROJECT) "Project" else "Global"

private fun SkillStatus.label() = when (this) {
    SkillStatus.ACTIVE -> "Active"
    SkillStatus.DISABLED -> "Off"
    SkillStatus.SHADOWED -> "Overridden"
    SkillStatus.INVALID -> "Needs attention"
}

private fun SkillFilter.label() = when (this) {
    SkillFilter.ALL -> "All skills"
    SkillFilter.ACTIVE -> "Active"
    SkillFilter.OFF -> "Off and overridden"
    SkillFilter.ISSUES -> "Needs attention"
}

private fun SkillFilter.shortLabel() = when (this) {
    SkillFilter.ALL -> "All"
    SkillFilter.ACTIVE -> "Active"
    SkillFilter.OFF -> "Off"
    SkillFilter.ISSUES -> "Issues"
}

@Composable
private fun GitPage(vm: ChatViewModel, settingsVm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var advanced by rememberSaveable { mutableStateOf(false) }
    Page("Git", onBack) {
        PcSectionLabel("GitHub")
        when {
            state.githubAuthCode != null -> {
                // Device flow in progress: show the code big, open the browser, keep polling.
                // Rows now paint their own card surface, so freeform group content does too.
                PcGroup {
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Spacing.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Enter this code on GitHub", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
                        Text(
                            state.githubAuthCode.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = PcMono, letterSpacing = 2.sp),
                            color = colors.onBackground,
                            modifier = Modifier.padding(vertical = Spacing.s),
                        )
                        PcButton("Open github.com/login/device") {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.githubVerifyUri ?: "https://github.com/login/device")))
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        PcButton("Cancel", filled = false) { vm.cancelGitHubSignIn() }
                    }
                }
                Note("Waiting for you to authorize on GitHub - this completes automatically.")
            }
            state.githubLogin != null -> {
                PcGroup {
                    PcRow {
                        Column(Modifier.weight(1f)) {
                            Text("@${state.githubLogin}", style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                            Text("Connected - push & pull enabled", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                        Text(
                            "Sign out",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.error,
                            modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable { vm.signOutGitHub() }.padding(8.dp),
                        )
                    }
                }
            }
            else -> {
                PcButton("Sign in with GitHub") { vm.startGitHubSignIn() }
                Spacer(Modifier.height(6.dp))
            }
        }
        PcSectionLabel("Advanced")
        PcGroup {
            ToggleRow("Show advanced", "Ignore unless you know git", checked = advanced) { advanced = it }
            if (advanced) {
                ToggleRow(
                    "Auto-branch each task",
                    "Each new chat works on its own branch of the project",
                    checked = settings.gitAutoBranch,
                ) { v -> settingsVm.update { it.copy(gitAutoBranch = v) } }
                var token by remember { mutableStateOf(vm.keyFor("git.token")) }
                PcRow {
                    Column(Modifier.weight(1f)) {
                        Text("Manual access token", style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                        Spacer(Modifier.height(6.dp))
                        PcField(token, { token = it; vm.setKey("git.token", it) }, "Fine-grained PAT (optional)", password = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportPage(vm: ChatViewModel, settingsVm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // reload AFTER the import lands: it overwrote app_settings.json on disk, and the
        // theme/instructions must go live without a restart (review finding #3).
        if (uri != null) vm.importFrom(uri) { settingsVm.reload() }
    }
    Page("Export & import", onBack) {
        PcSectionLabel("Your data")
        Note("Exports contain chats and settings, are not encrypted, and never include credentials.")
        Spacer(Modifier.height(10.dp))
        PcButton("Export chats & settings", filled = false) { exporter.launch("phonecode-backup-$stamp.zip") }
        Spacer(Modifier.height(10.dp))
        PcButton("Import from a file", filled = false) { importer.launch(arrayOf("application/zip", "application/octet-stream")) }
        state.notice?.let {
            Spacer(Modifier.height(10.dp))
            Note(it)
            LaunchedEffect(it) { kotlinx.coroutines.delay(4000); vm.clearNotice() }
        }
    }
}

@Composable
private fun AboutPage(vm: ChatViewModel, onOpenDoc: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0.1"
    }
    Page("About", onBack) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_phonecode_mark), null, tint = colors.onBackground, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(14.dp))
            Text("PhoneCode", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            Text("version $version", style = MaterialTheme.typography.labelMedium, color = colors.tertiary, modifier = Modifier.padding(top = 4.dp))
        }
        PcGroup {
            PcRow(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dttdrv.xyz/phonecode"))) }
            }) {
                Text("Website", style = MaterialTheme.typography.bodyLarge, color = colors.onBackground, modifier = Modifier.weight(1f))
                Text("dttdrv.xyz/phonecode", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
            }
            PcRow {
                Text("Config directory", style = MaterialTheme.typography.bodyLarge, color = colors.onBackground, modifier = Modifier.weight(1f))
                Text(vm.configDirPath().substringAfterLast("/"), style = MaterialTheme.typography.labelSmall.copy(fontFamily = PcMono), color = colors.tertiary)
            }
            NavRow("Terms of Service") { onOpenDoc("doc:terms") }
            NavRow("Privacy Policy") { onOpenDoc("doc:privacy") }
            NavRow("Open-source licenses") { onOpenDoc("doc:licenses") }
        }
    }
}

@Composable
private fun DocPage(title: String, assetName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val text = remember(assetName) {
        runCatching { context.assets.open(assetName).bufferedReader().use { it.readText() } }
            .getOrDefault("Document unavailable.")
    }
    Page(title, onBack) {
        // Render the markdown (headings, bold, lists) instead of dumping the raw source: the old single
        // Text showed literal '#', '**' and '-' in a tiny caption font. The doc's own H1 title is dropped
        // since the page header already shows it.
        val body = remember(text) { text.replace(Regex("^#\\s+.*(\\R+)?"), "") }
        Box(Modifier.padding(vertical = Spacing.xs)) {
            MarkdownBlocks(body, color = colors.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Custom provider dialog
// ---------------------------------------------------------------------------------------------

/**
 * Add a user-defined provider (round-3 feedback): name, base URL, wire format, and the model ids
 * to expose. Saved to providers.json - the same file the agent edits - so both arrival paths feed
 * one catalog. The API key is set afterwards on the provider's own detail page, like every preset.
 */
@Composable
private fun CustomProviderDialog(
    existingIds: Set<String>,
    onSave: (String, CustomProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var anthropicFormat by remember { mutableStateOf(false) }
    var modelsText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().shadow(24.dp, MaterialTheme.shapes.extraLarge, clip = false)
                .clip(MaterialTheme.shapes.extraLarge).background(colors.surfaceContainerHigh).padding(Spacing.m),
        ) {
            Text("Add custom provider", style = MaterialTheme.typography.titleMedium, color = colors.onBackground, modifier = Modifier.padding(bottom = Spacing.s))
            PcField(name, { name = it }, "Name (e.g. My LM Studio)")
            Spacer(Modifier.height(6.dp))
            PcField(baseUrl, { baseUrl = it }, "Base URL (e.g. https://host/v1)")
            Spacer(Modifier.height(6.dp))
            PcField(modelsText, { modelsText = it }, "Model ids, one per line", singleLine = false, minLines = 2)
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Anthropic format", style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                    Text("Off = OpenAI-compatible (most servers)", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                PcToggle(anthropicFormat, { anthropicFormat = it }, "Use Anthropic Messages format")
            }
            error?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.labelSmall, color = colors.error) }
            Spacer(Modifier.height(Spacing.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.weight(1f)) { PcButton("Cancel", filled = false, onClick = onDismiss) }
                Box(Modifier.weight(1f)) {
                    PcButton("Save") {
                        val id = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                        val models = modelsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        when {
                            name.isBlank() -> error = "Name is required"
                            id.isBlank() -> error = "Name needs at least one letter or digit"
                            id in existingIds -> error = "\"$id\" already exists"
                            !baseUrl.trim().startsWith("http") -> error = "Base URL must start with http(s)://"
                            models.isEmpty() -> error = "Add at least one model id"
                            else -> {
                                onSave(
                                    id,
                                    CustomProvider(
                                        name = name.trim(),
                                        baseUrl = baseUrl.trim().trimEnd('/'),
                                        format = if (anthropicFormat) "anthropic" else "openai",
                                        models = models.associateWith { CustomModel(name = it) },
                                    ),
                                )
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseHeaders(text: String): Map<String, String> =
    text.lineSequence().mapNotNull { line ->
        val i = line.indexOf(':')
        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toMap()
