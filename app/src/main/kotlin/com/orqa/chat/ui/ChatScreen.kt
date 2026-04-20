package com.orqa.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orqa.chat.data.MessageEntity
import com.orqa.chat.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onNavigateSettings: () -> Unit,
    onNavigateSync: () -> Unit
) {
    val state   = vm.state.collectAsState().value
    val listState = rememberLazyListState()
    val scope   = rememberCoroutineScope()
    val mono    = FontFamily.Monospace

    // Auto-scroll to bottom on new message
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.attachImage(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // ── Header ──────────────────────────────────────────────
        Surface(
            color  = BgSurface,
            border = BorderStroke(0.5.dp, BorderDim)
        ) {
            Row(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ORQA",
                    color      = Orange,
                    fontSize   = 11.sp,
                    fontFamily = mono,
                    letterSpacing = 2.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(" / ", color = TextDim, fontSize = 11.sp, fontFamily = mono)
                Text(
                    "DOC CHAT",
                    color     = TextMuted,
                    fontSize  = 11.sp,
                    fontFamily = mono,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.weight(1f))

                // Chunk count pill
                if (state.chunkCount > 0) {
                    Surface(
                        color  = BgSurface2,
                        shape  = RoundedCornerShape(3.dp),
                        border = BorderStroke(0.5.dp, BorderMid)
                    ) {
                        Text(
                            "${state.chunkCount} chunks",
                            color      = TextDim,
                            fontSize   = 9.sp,
                            fontFamily = mono,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                // Mode toggle
                ModeToggle(
                    mode       = state.chatMode,
                    onToggle   = { vm.onModeToggle() },
                    fontFamily = mono
                )

                Spacer(Modifier.width(6.dp))

                // Sync button
                IconButton(
                    onClick  = onNavigateSync,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint   = if (state.isSyncing) Orange else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings button
                IconButton(
                    onClick  = onNavigateSettings,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint   = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ── Provider badge ───────────────────────────────────────
        Surface(color = BgPrimary, border = BorderStroke(0.dp, Color.Transparent)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${state.provider} / ${state.model.take(24)}",
                    color      = TextDim,
                    fontSize   = 9.sp,
                    fontFamily = mono,
                    letterSpacing = 0.5.sp
                )
                if (state.chunkCount == 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No docs cached — tap Sync",
                        color    = Orange,
                        fontSize = 9.sp,
                        fontFamily = mono
                    )
                }
            }
        }

        HorizontalDivider(color = BorderDim, thickness = 0.5.dp)

        // ── Messages ─────────────────────────────────────────────
        LazyColumn(
            state    = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    EmptyState(
                        chunkCount = state.chunkCount,
                        onSync     = onNavigateSync,
                        mono       = mono
                    )
                }
            }
            items(state.messages, key = { it.id }) { msg ->
                MessageRow(msg = msg, mono = mono)
            }
            if (state.isLoading) {
                item { TypingIndicator(mono) }
            }
        }

        // ── Image preview ─────────────────────────────────────────
        AnimatedVisibility(visible = state.attachedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model  = state.attachedImageUri,
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(0.5.dp, BorderMid, RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Text("Image attached", color = TextMuted, fontSize = 11.sp, fontFamily = mono)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.clearImage() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove",
                        tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        HorizontalDivider(color = BorderDim, thickness = 0.5.dp)

        // ── Input row ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Image attach button
            IconButton(
                onClick  = { imagePicker.launch("image/*") },
                modifier = Modifier
                    .size(36.dp)
                    .border(0.5.dp, BorderMid, RoundedCornerShape(5.dp))
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Attach image",
                    tint     = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Text input
            OutlinedTextField(
                value         = state.inputText,
                onValueChange = vm::onInputChange,
                placeholder   = {
                    Text(
                        "Ask anything...",
                        color      = TextDim,
                        fontSize   = 13.sp,
                        fontFamily = mono
                    )
                },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.send() }),
                maxLines  = 5,
                colors    = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Orange,
                    unfocusedBorderColor = BorderMid,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = Orange,
                    focusedContainerColor   = BgPrimary,
                    unfocusedContainerColor = BgPrimary,
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = mono, fontSize = 13.sp
                ),
                shape = RoundedCornerShape(6.dp)
            )

            Spacer(Modifier.width(8.dp))

            // Send button
            Button(
                onClick  = { vm.send() },
                enabled  = !state.isLoading && (state.inputText.isNotBlank() || state.attachedImageB64.isNotEmpty()),
                colors   = ButtonDefaults.buttonColors(containerColor = Orange),
                modifier = Modifier.height(36.dp),
                shape    = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Text(
                    "SEND",
                    fontFamily    = mono,
                    fontSize      = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────

@Composable
fun MessageRow(msg: MessageEntity, mono: FontFamily) {
    val isUser    = msg.role == "user"
    val clipboard = LocalClipboardManager.current
    val sources   = try {
        val arr = JSONArray(msg.sources)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    Column(
        modifier         = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender label
        Text(
            if (isUser) "YOU" else "ORQA AI",
            color      = TextDim,
            fontSize   = 9.sp,
            fontFamily = mono,
            letterSpacing = 1.sp,
            modifier   = Modifier.padding(bottom = 2.dp)
        )

        // Image thumbnail (user message)
        if (isUser && msg.imageBase64.isNotEmpty()) {
            AsyncImage(
                model  = android.util.Base64.decode(msg.imageBase64, android.util.Base64.DEFAULT),
                contentDescription = "Attached image",
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, BlueBorder, RoundedCornerShape(6.dp))
                    .padding(bottom = 4.dp),
                contentScale = ContentScale.FillWidth
            )
        }

        // Bubble
        val bubbleBg     = if (isUser) BlueBg      else BgSurface
        val bubbleBorder = if (isUser) BlueBorder  else BorderDim
        val bubbleShape  = if (isUser)
            RoundedCornerShape(8.dp, 2.dp, 8.dp, 8.dp)
        else
            RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp)

        val leftBorderMod = if (!isUser && msg.role == "assistant" && /* engcall */ false)
            Modifier.border(
                BorderStroke(2.dp, Orange),
                shape = bubbleShape
            )
        else Modifier

        Surface(
            color  = bubbleBg,
            shape  = bubbleShape,
            border = BorderStroke(0.5.dp, bubbleBorder),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                msg.content,
                color      = if (isUser) BlueText else TextPrimary,
                fontSize   = 13.sp,
                fontFamily = mono,
                lineHeight = 20.sp,
                modifier   = Modifier.padding(9.dp, 8.dp)
            )
        }

        // Source tags
        if (sources.isNotEmpty()) {
            Row(
                modifier           = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sources.take(4).forEach { src ->
                    Surface(
                        color  = Color(0xFF1A1208),
                        shape  = RoundedCornerShape(3.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF3A2510))
                    ) {
                        Text(
                            src.substringAfterLast("/").take(30),
                            color    = Color(0xFFA05010),
                            fontSize = 9.sp,
                            fontFamily = mono,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // Category + copy row (assistant only)
        if (!isUser) {
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (msg.category.isNotEmpty()) {
                    Surface(
                        color  = Color(0x1AE8610A),
                        shape  = RoundedCornerShape(3.dp),
                        border = BorderStroke(0.5.dp, Color(0x4DE8610A))
                    ) {
                        Text(
                            msg.category,
                            color    = Orange,
                            fontSize = 9.sp,
                            fontFamily = mono,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                TextButton(
                    onClick  = { clipboard.setText(AnnotatedString(msg.content)) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Text(
                        "Copy",
                        color    = TextDim,
                        fontSize = 9.sp,
                        fontFamily = mono
                    )
                }
            }
        }
    }
}

// ── Mode toggle ───────────────────────────────────────────────────

@Composable
fun ModeToggle(mode: String, onToggle: () -> Unit, fontFamily: FontFamily) {
    Surface(
        color  = BgSurface2,
        shape  = RoundedCornerShape(5.dp),
        border = BorderStroke(0.5.dp, BorderMid)
    ) {
        Row {
            listOf("troubleshoot" to "DIAGNOSE", "engcall" to "ENG CALL").forEach { (m, label) ->
                val selected = mode == m
                Surface(
                    color    = if (selected) Orange else Color.Transparent,
                    shape    = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { if (!selected) onToggle() }
                ) {
                    Text(
                        label,
                        color      = if (selected) Color.White else TextDim,
                        fontSize   = 9.sp,
                        fontFamily = fontFamily,
                        letterSpacing = 0.8.sp,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────

@Composable
fun EmptyState(chunkCount: Int, onSync: () -> Unit, mono: FontFamily) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ORQA DOC CHAT",
            color      = Orange,
            fontSize   = 13.sp,
            fontFamily = mono,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        if (chunkCount == 0) {
            Text(
                "No docs cached yet.\nTap Sync to pull from GitHub or\npublic databases.",
                color      = TextMuted,
                fontSize   = 12.sp,
                fontFamily = mono,
                lineHeight = 18.sp,
                textAlign  = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onSync,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                border  = BorderStroke(0.5.dp, Orange)
            ) {
                Text("Sync Now", fontFamily = mono, fontSize = 11.sp)
            }
        } else {
            Text(
                "$chunkCount chunks indexed\nAsk anything about your ORQA gear",
                color     = TextMuted,
                fontSize  = 12.sp,
                fontFamily = mono,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ── Typing indicator ──────────────────────────────────────────────

@Composable
fun TypingIndicator(mono: FontFamily) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("ORQA AI", color = TextDim, fontSize = 9.sp, fontFamily = mono, letterSpacing = 1.sp)
        Spacer(Modifier.width(8.dp))
        repeat(3) { i ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot$i")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(600),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(i * 200)
                ), label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(TextMuted.copy(alpha = alpha), shape = RoundedCornerShape(50))
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}
