package com.apexaurum.pocket.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.ChatMessage
import com.apexaurum.pocket.soul.SoulData
import com.apexaurum.pocket.ui.components.ListeningIndicator
import com.apexaurum.pocket.ui.theme.*

@Composable
fun ChatScreen(
    soul: SoulData,
    messages: List<ChatMessage>,
    isChatting: Boolean,
    onSend: (String) -> Unit,
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    autoRead: Boolean = false,
    pendingVoiceText: String? = null,
    onToggleListening: () -> Unit = {},
    onToggleAutoRead: () -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onClearPendingVoice: () -> Unit = {},
    micAvailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Runtime permission launcher for RECORD_AUDIO
    var hasAudioPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) onToggleListening()
    }

    // Fill input with pending voice text for user review
    LaunchedEffect(pendingVoiceText) {
        if (pendingVoiceText != null) {
            inputText = pendingVoiceText
            onClearPendingVoice()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Agent header with auto-read toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "talking to ${soul.selectedAgentId}",
                color = Gold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
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
                ChatBubble(msg)
            }

            if (isChatting) {
                item {
                    Text(
                        text = "...",
                        color = Gold.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }
            }
        }

        // Listening indicator
        if (isListening) {
            ListeningIndicator()
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ApexDarkSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mic button (left of text input)
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
                    Text("speak...", color = TextMuted, fontFamily = FontFamily.Monospace)
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
                        if (inputText.isNotBlank() && !isChatting) {
                            onSend(inputText.trim())
                            inputText = ""
                        }
                    }
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank() && !isChatting) {
                        onSend(inputText.trim())
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isChatting,
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
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val bgColor = if (isUser) ApexSurface else Gold.copy(alpha = 0.1f)
    val textColor = if (isUser) TextPrimary else Gold

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 12.dp,
                    )
                )
                .background(bgColor)
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
