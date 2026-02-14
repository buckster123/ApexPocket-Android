package com.apexaurum.pocket.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            // Completed rounds (distinctBy guards against backend returning duplicate round entries)
            val uniqueRounds = session.rounds.distinctBy { it.roundNumber }
            items(uniqueRounds, key = { "round_${it.roundNumber}" }) { round ->
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
                            "Voice injected — round triggered",
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
                                Icons.AutoMirrored.Filled.Send,
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

/** Collapsible agent card — truncated when collapsed, tap to expand. */
@Composable
private fun AgentCard(
    agentId: String,
    content: String,
    isStreaming: Boolean,
    hasOutput: Boolean = true,
    toolCalls: List<CouncilToolCall>? = null,
) {
    val color = agentColor(agentId)
    // Streaming cards are always expanded; completed cards start collapsed
    var expanded by remember { mutableStateOf(isStreaming) }
    val isLong = content.length > 300

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ApexSurface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isStreaming && isLong) Modifier.clickable { expanded = !expanded }
                else Modifier
            ),
    ) {
        Column(
            Modifier
                .padding(10.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                ),
        ) {
            // Header row: agent name + expand/collapse indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    agentId,
                    color = color,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                if (!isStreaming && isLong) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            if (isStreaming && !hasOutput) {
                Text(
                    "thinking...",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic,
                )
            } else if (expanded || !isLong) {
                Text(
                    text = if (isStreaming && hasOutput) "$content|" else content,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                // Collapsed: show truncated preview
                Text(
                    text = content,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Tool calls (only when expanded or short)
            if (expanded || !isLong) {
                toolCalls?.forEach { tool ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${tool.name} → ${tool.result?.take(100) ?: "..."}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else if (!toolCalls.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${toolCalls.size} tool call${if (toolCalls.size > 1) "s" else ""}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic,
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

