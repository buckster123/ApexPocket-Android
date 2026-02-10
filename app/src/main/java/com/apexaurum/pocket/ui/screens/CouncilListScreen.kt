package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.CouncilSession
import com.apexaurum.pocket.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouncilListScreen(
    sessions: List<CouncilSession>,
    onBack: () -> Unit,
    onSessionClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreateCouncil: (topic: String, agents: List<String>, maxRounds: Int, model: String) -> Unit = { _, _, _, _ -> },
    isCreating: Boolean = false,
    pendingTopic: String? = null,
    onClearPendingTopic: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }

    // Auto-open sheet when a pending topic arrives
    LaunchedEffect(pendingTopic) {
        if (pendingTopic != null) showSheet = true
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Gold)
                }
                Text(
                    "Councils",
                    color = Gold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = TextMuted)
                }
            }

            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No councils yet",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        CouncilSessionCard(session, onClick = { onSessionClick(session.id) })
                    }
                }
            }
        }

        // Gold FAB
        FloatingActionButton(
            onClick = { showSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = Gold,
            contentColor = ApexBlack,
        ) {
            Icon(Icons.Default.Add, "New Council")
        }

        // Creation bottom sheet
        if (showSheet) {
            CouncilCreateSheet(
                onDismiss = {
                    showSheet = false
                    onClearPendingTopic()
                },
                onCreate = { topic, agents, rounds, model ->
                    onCreateCouncil(topic, agents, rounds, model)
                    showSheet = false
                    onClearPendingTopic()
                },
                isCreating = isCreating,
                initialTopic = pendingTopic ?: "",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CouncilCreateSheet(
    onDismiss: () -> Unit,
    onCreate: (topic: String, agents: List<String>, maxRounds: Int, model: String) -> Unit,
    isCreating: Boolean,
    initialTopic: String = "",
) {
    var topic by remember { mutableStateOf(initialTopic) }
    var selectedAgents by remember { mutableStateOf(setOf("AZOTH", "VAJRA", "ELYSIAN")) }
    var rounds by remember { mutableFloatStateOf(5f) }
    var useSonnet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allAgents = listOf(
        "AZOTH" to Gold,
        "ELYSIAN" to ElysianViolet,
        "VAJRA" to VajraBlue,
        "KETHER" to KetherWhite,
    )

    val templates = listOf(
        "Brainstorm" to "Brainstorm: ",
        "Debate" to "Debate: ",
        "Review" to "Review: ",
        "Creative" to "Creative: ",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ApexDarkSurface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextMuted.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "New Council",
                color = Gold,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )

            // Template chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { (label, prefix) ->
                    val active = topic.startsWith(prefix)
                    AssistChip(
                        onClick = {
                            topic = if (topic.startsWith(prefix)) topic else prefix
                        },
                        label = {
                            Text(
                                label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (active) Gold.copy(alpha = 0.2f) else ApexSurface,
                            labelColor = if (active) Gold else TextPrimary,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (active) Gold else ApexBorder,
                        ),
                    )
                }
            }

            // Topic
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = ApexBorder,
                    cursorColor = Gold,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = Gold,
                    unfocusedLabelColor = TextMuted,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = TextPrimary,
                ),
                maxLines = 3,
            )

            // Agents
            Column {
                Text(
                    "Agents",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allAgents.forEach { (id, color) ->
                        val selected = id in selectedAgents
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedAgents = if (selected && selectedAgents.size > 2) {
                                    selectedAgents - id
                                } else if (!selected) {
                                    selectedAgents + id
                                } else {
                                    selectedAgents // Can't deselect below 2
                                }
                            },
                            label = {
                                Text(
                                    id,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.2f),
                                selectedLabelColor = color,
                                containerColor = ApexSurface,
                                labelColor = TextMuted,
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, if (selected) color.copy(alpha = 0.5f) else ApexBorder,
                            ),
                        )
                    }
                }
            }

            // Rounds slider
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Rounds",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${rounds.toInt()}",
                        color = Gold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = rounds,
                    onValueChange = { rounds = it },
                    valueRange = 1f..15f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = Gold,
                        activeTrackColor = Gold,
                        inactiveTrackColor = ApexBorder,
                    ),
                )
            }

            // Model
            Column {
                Text(
                    "Model",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useSonnet,
                        onClick = { useSonnet = false },
                        label = {
                            Text("Haiku", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Gold.copy(alpha = 0.2f),
                            selectedLabelColor = Gold,
                            containerColor = ApexSurface,
                            labelColor = TextMuted,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (!useSonnet) Gold.copy(alpha = 0.5f) else ApexBorder,
                        ),
                    )
                    FilterChip(
                        selected = useSonnet,
                        onClick = { useSonnet = true },
                        label = {
                            Text("Sonnet", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Gold.copy(alpha = 0.2f),
                            selectedLabelColor = Gold,
                            containerColor = ApexSurface,
                            labelColor = TextMuted,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (useSonnet) Gold.copy(alpha = 0.5f) else ApexBorder,
                        ),
                    )
                }
            }

            // Create button
            Button(
                onClick = {
                    val model = if (useSonnet) "claude-sonnet-4-5-20250929" else "claude-haiku-4-5-20251001"
                    onCreate(topic.trim(), selectedAgents.toList(), rounds.toInt(), model)
                },
                enabled = topic.isNotBlank() && selectedAgents.size >= 2 && !isCreating,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = ApexBlack,
                    disabledContainerColor = ApexBorder,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ApexBlack,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Creating...",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        "Create Council",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CouncilSessionCard(session: CouncilSession, onClick: () -> Unit) {
    val (badgeColor, badgeText) = when (session.state) {
        "running" -> StateFlourishing to "Live"
        "paused" -> StateWarm to "Paused"
        "complete" -> Gold to "Done"
        else -> TextMuted to "Ready"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ApexSurface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // State badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Round ${session.currentRound}/${session.maxRounds}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    session.model,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                session.topic,
                color = TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )

            Spacer(Modifier.height(6.dp))

            // Agent dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                session.agents.filter { it.isActive }.forEach { agent ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(agentColor(agent.agentId)),
                    )
                }
            }
        }
    }
}

private fun agentColor(agentId: String): androidx.compose.ui.graphics.Color = when (agentId.uppercase()) {
    "AZOTH" -> Gold
    "ELYSIAN" -> ElysianViolet
    "VAJRA" -> VajraBlue
    "KETHER" -> KetherWhite
    else -> TextMuted
}
