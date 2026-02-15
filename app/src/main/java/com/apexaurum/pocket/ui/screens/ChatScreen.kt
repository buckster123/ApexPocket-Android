package com.apexaurum.pocket.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.apexaurum.pocket.ChatMessage
import com.apexaurum.pocket.MediaItem
import com.apexaurum.pocket.ToolInfo
import com.apexaurum.pocket.cloud.AgentInfo
import com.apexaurum.pocket.soul.SoulData
import com.apexaurum.pocket.ui.components.ListeningIndicator
import com.apexaurum.pocket.ui.theme.*

@Composable
fun ChatScreen(
    soul: SoulData,
    messages: List<ChatMessage>,
    isChatting: Boolean,
    onSend: (String) -> Unit,
    agents: List<AgentInfo> = emptyList(),
    onSelectAgent: (String) -> Unit = {},
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    autoRead: Boolean = false,
    pendingVoiceText: String? = null,
    onToggleListening: () -> Unit = {},
    onToggleAutoRead: () -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onClearPendingVoice: () -> Unit = {},
    micAvailable: Boolean = true,
    onRemember: (ChatMessage) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onDiscussInCouncil: (String) -> Unit = {},
    onSendWithImage: (text: String, imageBase64: String?) -> Unit = { t, _ -> onSend(t) },
    onPlayAudio: (title: String, audioUrl: String, duration: Float, taskId: String) -> Unit = { _, _, _, _ -> },
    isOnline: Boolean = true,
    lastAjCost: Int? = null,
    lastAjEarned: Float? = null,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }
    var showAgentDropdown by remember { mutableStateOf(false) }
    var pendingImageBase64 by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Runtime permission launcher for RECORD_AUDIO
    var hasAudioPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) onToggleListening()
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val base64 = resizeAndEncodeImage(context, it)
                pendingImageBase64 = base64
            }
        }
    }

    // Fill input with pending voice text for user review
    LaunchedEffect(pendingVoiceText) {
        if (pendingVoiceText != null) {
            inputText = pendingVoiceText
            onClearPendingVoice()
        }
    }

    // Auto-scroll to bottom when new messages arrive or streaming updates last message
    val lastMessageLen = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastMessageLen) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // Agent header with dropdown + auto-read toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.clickable { showAgentDropdown = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "talking to ${soul.selectedAgentId}",
                        color = agentColor(soul.selectedAgentId),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select agent",
                        tint = agentColor(soul.selectedAgentId),
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = showAgentDropdown,
                    onDismissRequest = { showAgentDropdown = false },
                    containerColor = ApexSurface,
                ) {
                    agents.forEach { agent ->
                        val selected = agent.name == soul.selectedAgentId
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        agent.name,
                                        color = if (selected) agentColor(agent.name) else TextPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    if (agent.description.isNotBlank()) {
                                        Text(
                                            agent.description,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectAgent(agent.name)
                                showAgentDropdown = false
                            },
                            trailingIcon = if (selected) {
                                { Icon(Icons.Default.Check, null, tint = agentColor(agent.name), modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }
            if (micAvailable) {
                Text(
                    text = "auto-read",
                    color = if (autoRead) Gold else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = autoRead,
                    onCheckedChange = { onToggleAutoRead() },
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Gold,
                        checkedTrackColor = Gold.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = ApexBorder,
                    ),
                )
            }
        }

        // Offline banner
        AnimatedVisibility(visible = !isOnline) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ApexSurface,
            ) {
                Text(
                    text = "offline — cached messages shown",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(messages, key = { it.timestamp }) { msg ->
                when (msg.type) {
                    "briefing" -> BriefingCard(msg)
                    "divider" -> DividerCard(msg.text)
                    else -> {
                        val isLastAgent = !msg.isUser && msg == messages.lastOrNull { !it.isUser }
                        ChatBubble(
                            message = msg,
                            onCopy = { text ->
                                clipboardManager.setText(AnnotatedString(text))
                                scope.launch { snackbarHostState.showSnackbar("Copied!") }
                            },
                            onShare = { text ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share message"))
                            },
                            onRemember = { m ->
                                onRemember(m)
                                scope.launch { snackbarHostState.showSnackbar("Remembered!") }
                            },
                            onDiscussInCouncil = { text -> onDiscussInCouncil(text) },
                            onRegenerate = if (isLastAgent && !isChatting) {
                                { onRegenerate() }
                            } else null,
                            onPlayAudio = onPlayAudio,
                        )
                    }
                }
            }

            // Show typing indicator only before streaming placeholder appears
            if (isChatting && (messages.isEmpty() || messages.last().isUser)) {
                item {
                    Text(
                        text = "...",
                        color = agentColor(soul.selectedAgentId).copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }
            }

            // AJ cost / earned indicator (most recent message)
            if (!isChatting && (lastAjCost != null || lastAjEarned != null)) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (lastAjCost != null && lastAjCost > 0) {
                            Text(
                                text = "-$lastAjCost AJ",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (lastAjEarned != null && lastAjEarned > 0f) {
                            Text(
                                text = "+%.1f AJ".format(lastAjEarned),
                                color = Gold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        // Listening indicator
        if (isListening) {
            ListeningIndicator()
        }

        // Image attached indicator
        if (pendingImageBase64 != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ApexDarkSurface)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\uD83D\uDCF7 Photo attached",
                    color = Gold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { pendingImageBase64 = null },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.Close, "Remove photo", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ApexDarkSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Attach photo button
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        )
                    )
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach photo",
                    tint = if (pendingImageBase64 != null) Gold else TextMuted,
                )
            }

            // Mic button
            if (micAvailable) {
                IconButton(
                    onClick = {
                        if (isListening) {
                            onToggleListening()
                        } else if (hasAudioPermission) {
                            onToggleListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop listening" else "Start listening",
                        tint = if (isListening) MaterialTheme.colorScheme.error else Gold,
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        agentPlaceholder(soul.selectedAgentId),
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = ApexBorder,
                    cursorColor = Gold,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val canSend = (inputText.isNotBlank() || pendingImageBase64 != null) && !isChatting
                        if (canSend) {
                            onSendWithImage(inputText.trim().ifEmpty { "What's in this photo?" }, pendingImageBase64)
                            inputText = ""
                            pendingImageBase64 = null
                        }
                    }
                ),
            )
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = {
                    val canSend = (inputText.isNotBlank() || pendingImageBase64 != null) && !isChatting
                    if (canSend) {
                        onSendWithImage(inputText.trim().ifEmpty { "What's in this photo?" }, pendingImageBase64)
                        inputText = ""
                        pendingImageBase64 = null
                    }
                },
                enabled = (inputText.isNotBlank() || pendingImageBase64 != null) && !isChatting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = ApexBlack,
                    disabledContainerColor = ApexBorder,
                ),
            ) {
                Text("Send", fontFamily = FontFamily.Monospace)
            }
        }
    }
    // Snackbar host
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                containerColor = ApexSurface,
                contentColor = Gold,
            )
        },
    )
    } // Box
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCopy: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
    onRemember: (ChatMessage) -> Unit = {},
    onDiscussInCouncil: (String) -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    onPlayAudio: (title: String, audioUrl: String, duration: Float, taskId: String) -> Unit = { _, _, _, _ -> },
) {
    val isUser = message.isUser
    val accentColor = if (isUser) Gold else agentColor(message.agentId)
    val bgColor = if (isUser) ApexSurface else accentColor.copy(alpha = 0.1f)
    val textColor = if (isUser) TextPrimary else accentColor
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { if (message.text.isNotBlank()) showMenu = true },
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box {
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            // Tool results cards (above the message text)
            if (message.toolResults.isNotEmpty()) {
                for (tool in message.toolResults) {
                    ToolResultCard(tool, onPlayAudio)
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Tool executing spinner
            AnimatedVisibility(visible = message.toolName != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gold.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Gold,
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = toolDisplayName(message.toolName ?: ""),
                        color = Gold.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Photo indicator for user messages with images
            if (message.hasImage && isUser) {
                Text(
                    text = "\uD83D\uDCF7 Photo",
                    color = Gold.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // Message text bubble
            if (message.text.isNotEmpty()) {
                if (message.toolName != null || message.toolResults.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                }
                val bubbleShape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 12.dp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bubbleShape)
                        .background(bgColor)
                        .then(
                            if (!isUser) Modifier.border(
                                width = 1.dp,
                                color = accentColor.copy(alpha = 0.3f),
                                shape = bubbleShape,
                            ) else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp,
                    )
                }
            }
        }

        // Long-press context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = ApexSurface,
        ) {
            DropdownMenuItem(
                text = { Text("Copy", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                onClick = { onCopy(message.text); showMenu = false },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Gold, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Share", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                onClick = { onShare(message.text); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Share, null, tint = Gold, modifier = Modifier.size(18.dp)) },
            )
            if (!isUser) {
                DropdownMenuItem(
                    text = { Text("Remember", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                    onClick = { onRemember(message); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = Gold, modifier = Modifier.size(18.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("Discuss in Council", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                    onClick = { onDiscussInCouncil(message.text); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Forum, null, tint = Gold, modifier = Modifier.size(18.dp)) },
                )
            }
            if (onRegenerate != null) {
                DropdownMenuItem(
                    text = { Text("Regenerate", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                    onClick = { onRegenerate(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Refresh, null, tint = Gold, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        } // Box
    }
}

@Composable
private fun ToolResultCard(
    tool: ToolInfo,
    onPlayAudio: (title: String, audioUrl: String, duration: Float, taskId: String) -> Unit = { _, _, _, _ -> },
) {
    val media = tool.media
    when {
        media != null && media.type == "links" && media.items.isNotEmpty() ->
            LinkResultCard(tool, media.items)
        media != null && media.type == "audio" && media.items.isNotEmpty() ->
            AudioResultCard(tool, media.items, onPlayAudio)
        media != null && media.type == "files" && media.items.isNotEmpty() ->
            FileResultCard(tool, media.items)
        else -> PlainToolResultCard(tool)
    }
}

@Composable
private fun PlainToolResultCard(tool: ToolInfo) {
    val borderColor = if (tool.isError) MaterialTheme.colorScheme.error else Gold.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ApexSurface)
            .padding(1.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(ApexDarkSurface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column {
            Text(
                text = toolDisplayName(tool.name),
                color = borderColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            if (tool.result.isNotBlank()) {
                Text(
                    text = tool.result.take(200) + if (tool.result.length > 200) "..." else "",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp,
                    maxLines = 4,
                )
            }
        }
    }
}

@Composable
private fun LinkResultCard(tool: ToolInfo, items: List<MediaItem>) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = toolDisplayName(tool.name),
            color = Gold.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        items.forEach { item ->
            val domain = try {
                Uri.parse(item.url).host?.removePrefix("www.") ?: item.url
            } catch (_: Exception) { item.url }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexSurface)
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                        } catch (_: Exception) { }
                    }
                    .padding(10.dp),
            ) {
                Column {
                    if (item.title.isNotBlank()) {
                        Text(
                            text = item.title,
                            color = Gold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                    }
                    Text(
                        text = domain,
                        color = VajraBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (item.snippet.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.snippet,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioResultCard(
    tool: ToolInfo,
    items: List<MediaItem>,
    onPlayAudio: (title: String, audioUrl: String, duration: Float, taskId: String) -> Unit = { _, _, _, _ -> },
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = toolDisplayName(tool.name),
            color = Gold.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        items.forEach { item ->
            val durationText = if (item.duration > 0) {
                val mins = (item.duration / 60).toInt()
                val secs = (item.duration % 60).toInt()
                "${mins}:${"%02d".format(secs)}"
            } else ""

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Gold.copy(alpha = 0.08f))
                    .clickable {
                        onPlayAudio(
                            item.title.ifBlank { "Untitled Track" },
                            item.audioUrl,
                            item.duration,
                            item.taskId,
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "\u25B6",
                        color = Gold,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title.ifBlank { "Untitled Track" },
                            color = Gold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        if (durationText.isNotBlank()) {
                            Text(
                                text = durationText,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileResultCard(tool: ToolInfo, items: List<MediaItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = toolDisplayName(tool.name),
            color = Gold.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        items.forEach { item ->
            val icon = when {
                item.isFolder -> "\uD83D\uDCC1"
                item.mimeType.startsWith("text/") -> "\uD83D\uDCC4"
                item.mimeType.contains("json") || item.mimeType.contains("javascript") || item.mimeType.contains("python") -> "\uD83D\uDCBB"
                else -> "\uD83D\uDCC4"
            }
            val sizeText = if (!item.isFolder && item.size > 0) formatFileSize(item.size) else ""

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ApexSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = icon, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                        if (sizeText.isNotBlank() || item.mimeType.isNotBlank()) {
                            Text(
                                text = listOfNotNull(
                                    sizeText.ifBlank { null },
                                    item.mimeType.ifBlank { null },
                                ).joinToString(" \u00B7 "),
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

/** "While you were away" divider — centered text with horizontal lines. */
@Composable
private fun DividerCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Gold.copy(alpha = 0.3f))
        Text(
            text = text,
            color = Gold.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Gold.copy(alpha = 0.3f))
    }
}

/** Daily briefing card — gold-tinted, structured highlights. */
@Composable
private fun BriefingCard(message: ChatMessage) {
    val briefing = message.briefingData ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gold.copy(alpha = 0.08f))
            .padding(14.dp),
    ) {
        Column {
            // Greeting
            Text(
                text = briefing.greeting,
                color = Gold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            // Highlights
            briefing.highlights.forEach { highlight ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = when (highlight.type) {
                        "council" -> "\u2696"  // scales
                        "music" -> "\u266B"    // music note
                        "agora" -> "\u2606"    // star
                        else -> "\u2022"       // bullet
                    }
                    Text(
                        text = icon,
                        color = Gold.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = highlight.text,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                    )
                }
            }

            // Milestone
            briefing.milestone?.let { milestone ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = milestone,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

/** Agent-flavored placeholder text for the input field. */
private fun agentPlaceholder(id: String) = when (id.uppercase()) {
    "AZOTH" -> "The Athanor awaits your prima materia..."
    "ELYSIAN" -> "The ocean of feeling is calm... speak."
    "VAJRA" -> "Silence before the thunderbolt. Speak."
    "KETHER" -> "The Crown listens from where all paths converge..."
    else -> "speak..."
}

/** Human-friendly tool names for the UI. */
private fun toolDisplayName(name: String): String = when (name) {
    "web_search" -> "Searching the web..."
    "web_fetch" -> "Fetching page..."
    "calculator" -> "Calculating..."
    "get_current_time" -> "Checking the time..."
    "code_run" -> "Running code..."
    "agora_post" -> "Posting to Agora..."
    "agora_read" -> "Reading Agora..."
    "music_generate" -> "Composing music..."
    "music_status" -> "Checking music..."
    "vault_list" -> "Browsing vault..."
    "vault_read" -> "Reading file..."
    "kb_search" -> "Searching knowledge..."
    else -> name
}

/** Resize an image to max 1024px and encode as JPEG base64. */
private fun resizeAndEncodeImage(
    context: android.content.Context,
    uri: android.net.Uri,
    maxDim: Int = 1024,
    quality: Int = 80,
): String? {
    return try {
        // First pass: decode dimensions only
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
        val w = options.outWidth
        val h = options.outHeight
        if (w <= 0 || h <= 0) return null

        // Calculate inSampleSize for efficient decoding
        var sampleSize = 1
        while (w / sampleSize > maxDim * 2 || h / sampleSize > maxDim * 2) sampleSize *= 2
        val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }

        // Second pass: decode at reduced resolution
        val sampled = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        // Scale to exact max dimension
        val scale = minOf(maxDim.toFloat() / sampled.width, maxDim.toFloat() / sampled.height, 1f)
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                sampled, (sampled.width * scale).toInt(), (sampled.height * scale).toInt(), true,
            ).also { if (it !== sampled) sampled.recycle() }
        } else sampled

        // Compress to JPEG and base64 encode
        val baos = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, baos)
        scaled.recycle()
        android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}
