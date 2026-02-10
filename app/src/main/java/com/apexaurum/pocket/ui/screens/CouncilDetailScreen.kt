package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.*
import com.apexaurum.pocket.ui.theme.*

@Composable
fun CouncilDetailScreen(
    session: CouncilSessionDetail?,
    agentOutputs: Map<String, String>,
    currentRound: Int,
    isStreaming: Boolean,
    buttInSent: Boolean,
    onBack: () -> Unit,
    onButtIn: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (session == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold)
        }
        return
    }

    val isLive = session.state == "running" && isStreaming
    var buttInText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll when new agent output arrives
    LaunchedEffect(agentOutputs.values.sumOf { it.length }) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Column(modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Gold)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    session.topic,
                    color = Gold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (badgeColor, badgeText) = stateInfo(session.state)
                    Text(
                        badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (isLive) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(StateFlourishing),
                        )
                    }
                    Text(
                        " · R${session.currentRound}/${session.maxRounds} · ${session.model}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // Rounds
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = if (session.state == "running") 8.dp else 16.dp),
        ) {
            // Completed rounds
            items(session.rounds, key = { it.roundNumber }) { round ->
                RoundCard(round)
            }

            // Live round (if streaming)
            if (isLive && agentOutputs.isNotEmpty()) {
                item(key = "live_round") {
                    LiveRoundCard(currentRound, agentOutputs, session.agents)
                }
            }
        }

        // Butt-in input (only for running sessions)
        if (session.state == "running") {
            Surface(color = ApexDarkSurface) {
                Column(Modifier.padding(12.dp)) {
                    if (buttInSent) {
                        Text(
                            "Voice will be heard next round",
                            color = StateWarm,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().imePadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = buttInText,
                            onValueChange = { buttInText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Inject your voice...",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            },
                            enabled = !buttInSent,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold,
                                unfocusedBorderColor = ApexBorder,
                                cursorColor = Gold,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                            ),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (buttInText.isNotBlank()) {
                                    onButtIn(buttInText)
                                    buttInText = ""
                                }
                            },
                            enabled = !buttInSent && buttInText.isNotBlank(),
                        ) {
                            Icon(
                                Icons.Default.Send,
                                "Butt in",
                                tint = if (buttInSent) TextMuted else Gold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundCard(round: CouncilRound) {
    Column {
        // Round header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Round ${round.roundNumber}",
                color = Gold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            if (round.convergenceScore > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "%.0f%%".format(round.convergenceScore * 100),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Human message (butt-in)
        round.humanMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = StateWarm.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "HUMAN",
                        color = StateWarm,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        msg,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Agent messages
        round.messages.filter { it.role == "agent" }.forEach { msg ->
            AgentCard(
                agentId = msg.agentId ?: "UNKNOWN",
                content = msg.content,
                isStreaming = false,
                toolCalls = msg.toolCalls,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LiveRoundCard(
    roundNumber: Int,
    agentOutputs: Map<String, String>,
    agents: List<CouncilAgentInfo>,
) {
    Column {
        Text(
            "Round $roundNumber",
            color = Gold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        agents.filter { it.isActive }.forEach { agent ->
            val text = agentOutputs[agent.agentId]
            AgentCard(
                agentId = agent.agentId,
                content = text ?: "",
                isStreaming = true,
                hasOutput = text != null,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AgentCard(
    agentId: String,
    content: String,
    isStreaming: Boolean,
    hasOutput: Boolean = true,
    toolCalls: List<CouncilToolCall>? = null,
) {
    val color = agentColor(agentId)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ApexSurface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                agentId,
                color = color,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))

            if (isStreaming && !hasOutput) {
                Text(
                    "thinking...",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic,
                )
            } else {
                Text(
                    text = if (isStreaming && hasOutput) "$content|" else content,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            toolCalls?.forEach { tool ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "${tool.name} → ${tool.result?.take(100) ?: "..."}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun stateInfo(state: String): Pair<androidx.compose.ui.graphics.Color, String> = when (state) {
    "running" -> StateFlourishing to "Live"
    "paused" -> StateWarm to "Paused"
    "complete" -> Gold to "Done"
    else -> TextMuted to "Ready"
}

private fun agentColor(agentId: String): androidx.compose.ui.graphics.Color = when (agentId.uppercase()) {
    "AZOTH" -> Gold
    "ELYSIAN" -> ElysianViolet
    "VAJRA" -> VajraBlue
    "KETHER" -> KetherWhite
    else -> TextMuted
}
