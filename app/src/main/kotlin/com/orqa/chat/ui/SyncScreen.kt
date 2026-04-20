package com.orqa.chat.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orqa.chat.data.SyncSource
import com.orqa.chat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val state   = vm.state.collectAsState().value
    val sources = vm.syncSources.collectAsState().value
    val mono    = FontFamily.Monospace

    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Header
        Surface(color = BgSurface, border = BorderStroke(0.5.dp, BorderDim)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("DATA SOURCES", color = TextPrimary, fontSize = 11.sp,
                    fontFamily = mono, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                Text("${state.chunkCount} chunks cached",
                    color = TextDim, fontSize = 10.sp, fontFamily = mono)
            }
        }
        HorizontalDivider(color = BorderDim, thickness = 0.5.dp)

        // Sync status
        if (state.syncStatus.isNotEmpty()) {
            Surface(color = BgSurface2) {
                Text(
                    state.syncStatus,
                    color    = if ("error" in state.syncStatus.lowercase()) RedError else GreenOk,
                    fontSize = 10.sp,
                    fontFamily = mono,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp, 6.dp)
                )
            }
            HorizontalDivider(color = BorderDim, thickness = 0.5.dp)
        }

        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sync all button
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { vm.syncAll() },
                        enabled  = !state.isSyncing,
                        colors   = ButtonDefaults.buttonColors(containerColor = Orange),
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(6.dp)
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("SYNC ALL", fontFamily = mono, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                    OutlinedButton(
                        onClick = { vm.clearCache() },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
                        border  = BorderStroke(0.5.dp, RedError),
                        shape   = RoundedCornerShape(6.dp)
                    ) {
                        Text("CLEAR CACHE", fontFamily = mono, fontSize = 10.sp)
                    }
                }
            }

            // Section header
            item {
                Text("SOURCES", color = TextDim, fontSize = 9.sp,
                    fontFamily = mono, letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }

            // Source cards
            items(sources, key = { it.id }) { source ->
                SourceCard(
                    source  = source,
                    isSyncing = state.isSyncing,
                    onSync  = { vm.syncOne(source) },
                    onToggle = { vm.toggleSyncSource(source) },
                    onDelete = { vm.deleteSyncSource(source) },
                    mono    = mono
                )
            }

            // Add source button
            item {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                    border  = BorderStroke(0.5.dp, BorderMid),
                    shape   = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ADD SOURCE", fontFamily = mono, fontSize = 10.sp)
                }
            }
        }
    }

    // Add source dialog
    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd     = { source ->
                vm.addSyncSource(source)
                showAddDialog = false
            },
            mono = mono
        )
    }
}

@Composable
fun SourceCard(
    source: SyncSource,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    mono: FontFamily
) {
    Surface(
        color  = BgSurface,
        shape  = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, if (source.enabled) BorderMid else BorderDim)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.label, color = TextPrimary, fontSize = 12.sp, fontFamily = mono)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append(source.type.uppercase())
                            if (source.url.isNotEmpty()) append(" · ${source.url.take(40)}")
                        },
                        color = TextDim, fontSize = 9.sp, fontFamily = mono
                    )
                }
                Switch(
                    checked         = source.enabled,
                    onCheckedChange = { onToggle() },
                    colors          = SwitchDefaults.colors(checkedThumbColor = Orange, checkedTrackColor = OrangeDim)
                )
            }

            if (source.enabled) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Stats
                    if (source.lastSyncAt > 0) {
                        Surface(color = BgSurface2, shape = RoundedCornerShape(3.dp)) {
                            Text(
                                "${source.fileCount} files · ${source.chunkCount} chunks",
                                color = TextDim, fontSize = 9.sp, fontFamily = mono,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (source.errorMsg.isNotEmpty()) {
                        Text(
                            source.errorMsg.take(50),
                            color = RedError, fontSize = 9.sp, fontFamily = mono
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Sync button
                    OutlinedButton(
                        onClick  = onSync,
                        enabled  = !isSyncing,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                        border   = BorderStroke(0.5.dp, OrangeDim),
                        shape    = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(26.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("SYNC", fontFamily = mono, fontSize = 9.sp)
                    }
                    // Delete button
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Delete, null, tint = TextDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (SyncSource) -> Unit,
    mono: FontFamily
) {
    var label  by remember { mutableStateOf("") }
    var type   by remember { mutableStateOf("github") }
    var url    by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        title = {
            Text("Add Source", color = TextPrimary, fontFamily = mono, fontSize = 13.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type selector
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("github" to "GitHub Repo", "public_url" to "Public URL").forEach { (t, label2) ->
                        val sel = type == t
                        OutlinedButton(
                            onClick = { type = t },
                            colors  = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (sel) OrangeDim else Color.Transparent,
                                contentColor   = if (sel) Orange else TextMuted
                            ),
                            border = BorderStroke(0.5.dp, if (sel) Orange else BorderMid),
                            shape  = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(label2, fontFamily = mono, fontSize = 9.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label", fontFamily = mono, fontSize = 11.sp) },
                    colors = dialogFieldColors(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = {
                        Text(
                            if (type == "github") "Owner/Repo  (e.g. DroneWuKong/droneclear_Forge)"
                            else "URL  (e.g. https://example.com/file.json)",
                            fontFamily = mono, fontSize = 10.sp
                        )
                    },
                    colors   = dialogFieldColors(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank() && url.isNotBlank()) {
                        onAdd(SyncSource(
                            id    = "$type:${url.substringAfterLast("/")}",
                            type  = type,
                            label = label,
                            url   = url
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("ADD", fontFamily = mono, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = mono, fontSize = 11.sp, color = TextMuted)
            }
        }
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Orange,
    unfocusedBorderColor = BorderMid,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary,
    cursorColor          = Orange,
    focusedContainerColor   = BgSurface2,
    unfocusedContainerColor = BgSurface2,
    focusedLabelColor    = Orange,
    unfocusedLabelColor  = TextDim
)
