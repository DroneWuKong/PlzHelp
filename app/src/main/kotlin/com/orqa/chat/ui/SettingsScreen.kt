package com.orqa.chat.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orqa.chat.data.SettingsStore
import com.orqa.chat.ui.theme.*

data class ProviderOption(
    val id: String,
    val label: String,
    val desc: String,
    val badge: String,
    val models: List<String>
)

val PROVIDERS = listOf(
    ProviderOption("gemini", "Gemini", "Google AI Studio — free tier", "FREE",
        listOf("gemini-2.0-flash", "gemini-2.5-flash-preview-05-20", "gemini-2.5-pro-preview-05-06")),
    ProviderOption("anthropic", "Claude", "Anthropic — pay per message", "PAID",
        listOf("claude-haiku-4-5-20251001", "claude-sonnet-4-20250514")),
    ProviderOption("local", "Local Server", "laptop running server.py on same WiFi", "LOCAL",
        listOf("auto")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val state    = vm.state.collectAsState().value
    val settings = vm.settings
    val mono     = FontFamily.Monospace
    val scroll   = rememberScrollState()

    // Load current values
    var geminiKey    by remember { mutableStateOf("") }
    var anthropicKey by remember { mutableStateOf("") }
    var githubToken  by remember { mutableStateOf("") }
    var githubUser   by remember { mutableStateOf("DroneWuKong") }
    var localUrl     by remember { mutableStateOf("http://192.168.1.100:5000") }
    var selProvider  by remember { mutableStateOf(state.provider) }
    var selModel     by remember { mutableStateOf(state.model) }
    var saved        by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        geminiKey    = settings.getString(SettingsStore.GEMINI_KEY)
        anthropicKey = settings.getString(SettingsStore.ANTHROPIC_KEY)
        githubToken  = settings.getString(SettingsStore.GITHUB_TOKEN)
        githubUser   = settings.getString(SettingsStore.GITHUB_USERNAME).ifEmpty { "DroneWuKong" }
        localUrl     = settings.getString(SettingsStore.LOCAL_SERVER_URL).ifEmpty { "http://192.168.1.100:5000" }
        selProvider  = settings.getString(SettingsStore.PROVIDER).ifEmpty { "gemini" }
        selModel     = settings.getString(SettingsStore.MODEL).ifEmpty { "gemini-2.0-flash" }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        // Header
        Surface(color = BgSurface, border = BorderStroke(0.5.dp, BorderDim)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("SETTINGS", color = TextPrimary, fontSize = 11.sp,
                    fontFamily = mono, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                if (saved) {
                    Text("Saved ✓", color = GreenOk, fontSize = 10.sp, fontFamily = mono)
                }
            }
        }
        HorizontalDivider(color = BorderDim, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── LLM Provider ──────────────────────────────────────
            SettingsSection("LLM PROVIDER", mono) {
                PROVIDERS.forEach { prov ->
                    val sel = selProvider == prov.id
                    Surface(
                        color  = if (sel) Color(0x0DE8610A) else BgSurface,
                        shape  = RoundedCornerShape(7.dp),
                        border = BorderStroke(if (sel) 1.dp else 0.5.dp, if (sel) Orange else BorderMid),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selProvider = prov.id
                            selModel    = prov.models.first()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sel,
                                onClick  = { selProvider = prov.id; selModel = prov.models.first() },
                                colors   = RadioButtonDefaults.colors(selectedColor = Orange)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prov.label, color = TextPrimary, fontSize = 12.sp, fontFamily = mono)
                                Text(prov.desc, color = TextDim, fontSize = 9.sp, fontFamily = mono)
                            }
                            Surface(
                                color  = when (prov.badge) {
                                    "FREE"  -> Color(0xFF0F2A0F)
                                    "LOCAL" -> Color(0xFF1A1208)
                                    else    -> Color(0xFF1A1229)
                                },
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    prov.badge,
                                    color = when (prov.badge) {
                                        "FREE"  -> Color(0xFF4ADE80)
                                        "LOCAL" -> Color(0xFFFB923C)
                                        else    -> Color(0xFF818CF8)
                                    },
                                    fontSize = 8.sp, fontFamily = mono,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    // Model selector (visible when this provider is selected)
                    if (sel && prov.models.size > 1) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            prov.models.forEach { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selModel = m }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selModel == m,
                                        onClick  = { selModel = m },
                                        colors   = RadioButtonDefaults.colors(selectedColor = Orange),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(m, color = if (selModel == m) Orange else TextMuted,
                                        fontSize = 11.sp, fontFamily = mono)
                                }
                            }
                        }
                    }
                }
            }

            // ── API Keys ──────────────────────────────────────────
            SettingsSection("API KEYS", mono) {
                KeyField("Gemini API Key  (AIzaSy...)", geminiKey,
                    hint = "Free at aistudio.google.com — same key Wingman uses") { geminiKey = it }
                Spacer(Modifier.height(8.dp))
                KeyField("Anthropic API Key  (sk-ant-...)", anthropicKey,
                    hint = "console.anthropic.com") { anthropicKey = it }
                Spacer(Modifier.height(8.dp))
                KeyField("Local Server URL", localUrl, isPassword = false,
                    hint = "IP of laptop running server.py on same WiFi") { localUrl = it }
            }

            // ── GitHub ────────────────────────────────────────────
            SettingsSection("GITHUB", mono) {
                KeyField("GitHub Token  (ghp_...)", githubToken,
                    hint = "github.com/settings/tokens — needs repo scope for private repos") { githubToken = it }
                Spacer(Modifier.height(8.dp))
                SettingsTextField("GitHub Username", githubUser, mono) { githubUser = it }
            }
        }

        // Save button
        HorizontalDivider(color = BorderDim, thickness = 0.5.dp)
        Surface(color = BgSurface) {
            Button(
                onClick = {
                    val s = vm.settings
                    kotlinx.coroutines.MainScope().launch {
                        s.set(SettingsStore.PROVIDER,          selProvider)
                        s.set(SettingsStore.MODEL,             selModel)
                        s.set(SettingsStore.GEMINI_KEY,        geminiKey)
                        s.set(SettingsStore.ANTHROPIC_KEY,     anthropicKey)
                        s.set(SettingsStore.GITHUB_TOKEN,      githubToken)
                        s.set(SettingsStore.GITHUB_USERNAME,   githubUser)
                        s.set(SettingsStore.LOCAL_SERVER_URL,  localUrl)
                    }
                    saved = true
                },
                colors   = ButtonDefaults.buttonColors(containerColor = Orange),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                shape    = RoundedCornerShape(6.dp)
            ) {
                Text("SAVE SETTINGS", fontFamily = mono, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, mono: FontFamily, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = TextDim, fontSize = 9.sp, fontFamily = mono,
            letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
        Surface(color = BgSurface, shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, BorderDim)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun KeyField(
    label: String,
    value: String,
    hint: String = "",
    isPassword: Boolean = true,
    onChange: (String) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    val mono = FontFamily.Monospace
    Column {
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            label = { Text(label, fontFamily = mono, fontSize = 10.sp) },
            visualTransformation = if (isPassword && !show)
                PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) ({
                IconButton(onClick = { show = !show }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = TextDim, modifier = Modifier.size(16.dp)
                    )
                }
            }) else null,
            colors   = settingsFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 11.sp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (hint.isNotEmpty()) {
            Text(hint, color = TextDim, fontSize = 9.sp, fontFamily = mono,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp))
        }
    }
}

@Composable
fun SettingsTextField(label: String, value: String, mono: FontFamily, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = mono, fontSize = 10.sp) },
        colors = settingsFieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 11.sp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Orange,
    unfocusedBorderColor    = BorderMid,
    focusedTextColor        = TextPrimary,
    unfocusedTextColor      = TextPrimary,
    cursorColor             = Orange,
    focusedContainerColor   = BgSurface2,
    unfocusedContainerColor = BgSurface2,
    focusedLabelColor       = Orange,
    unfocusedLabelColor     = TextDim
)

private fun <T> kotlinx.coroutines.MainScope() = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
