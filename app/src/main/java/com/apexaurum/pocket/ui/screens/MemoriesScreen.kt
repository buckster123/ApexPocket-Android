package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.AgentMemoryItem
import com.apexaurum.pocket.cloud.CortexMemoryNode
import com.apexaurum.pocket.cloud.CortexStatsResponse
import com.apexaurum.pocket.cloud.DreamStatusResponse
import com.apexaurum.pocket.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoriesScreen(
    // Legacy agent memories
    memories: List<AgentMemoryItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSave: (key: String, value: String, type: String) -> Unit,
    onDelete: (memoryId: String) -> Unit,
    // CerebroCortex
    cortexMemories: List<CortexMemoryNode>,
    cortexLoading: Boolean,
    cortexSearchQuery: String,
    cortexStats: CortexStatsResponse?,
    dreamStatus: DreamStatusResponse?,
    dreamTriggering: Boolean,
    onFetchCortex: () -> Unit,
    onSearchCortex: (String) -> Unit,
    onDeleteCortex: (String) -> Unit,
    onFetchCortexStats: () -> Unit,
    onFetchDreamStatus: () -> Unit,
    onTriggerDream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var subNav by remember { mutableStateOf("cortex") }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AgentMemoryItem?>(null) }
    var deleteCortexTarget by remember { mutableStateOf<CortexMemoryNode?>(null) }

    // Auto-fetch on first composition
    LaunchedEffect(Unit) {
        onFetchCortex()
        onFetchCortexStats()
        onFetchDreamStatus()
    }

    Scaffold(
        containerColor = ApexBlack,
        floatingActionButton = {
            if (subNav == "agent") {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Gold,
                    contentColor = ApexBlack,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── Sub-navigation chips ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf("cortex" to "Cortex", "agent" to "Agent", "dream" to "Dream").forEach { (key, label) ->
                    FilterChip(
                        selected = subNav == key,
                        onClick = { subNav = key },
                        label = {
                            Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Gold.copy(alpha = 0.2f),
                            selectedLabelColor = Gold,
                            containerColor = ApexSurface,
                            labelColor = TextMuted,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = ApexBorder,
                            selectedBorderColor = Gold.copy(alpha = 0.4f),
                            enabled = true,
                            selected = subNav == key,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Content ──
            when (subNav) {
                "cortex" -> CortexTab(
                    memories = cortexMemories,
                    isLoading = cortexLoading,
                    searchQuery = cortexSearchQuery,
                    stats = cortexStats,
                    onSearch = onSearchCortex,
                    onRefresh = onFetchCortex,
                    onDelete = { deleteCortexTarget = it },
                )
                "agent" -> AgentTab(
                    memories = memories,
                    isLoading = isLoading,
                    onRefresh = onRefresh,
                    onDelete = { deleteTarget = it },
                )
                "dream" -> DreamTab(
                    status = dreamStatus,
                    isTriggering = dreamTriggering,
                    onRefresh = onFetchDreamStatus,
                    onTrigger = onTriggerDream,
                )
            }
        }
    }

    // Add Memory Dialog (agent tab only)
    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { key, value, type ->
                onSave(key, value, type)
                showAddDialog = false
            },
        )
    }

    // Delete agent memory confirmation
    deleteTarget?.let { memory ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = ApexSurface,
            titleContentColor = Gold,
            textContentColor = TextPrimary,
            title = { Text("Delete Memory", fontFamily = FontFamily.Monospace) },
            text = { Text("Delete \"${memory.key}\"?", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(memory.id); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Gold),
                ) { Text("Delete", fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
                ) { Text("Cancel", fontFamily = FontFamily.Monospace) }
            },
        )
    }

    // Delete cortex memory confirmation
    deleteCortexTarget?.let { memory ->
        AlertDialog(
            onDismissRequest = { deleteCortexTarget = null },
            containerColor = ApexSurface,
            titleContentColor = Gold,
            textContentColor = TextPrimary,
            title = { Text("Delete Memory", fontFamily = FontFamily.Monospace) },
            text = {
                Text(
                    memory.content.take(100) + if (memory.content.length > 100) "..." else "",
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteCortex(memory.id); deleteCortexTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Gold),
                ) { Text("Delete", fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteCortexTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
                ) { Text("Cancel", fontFamily = FontFamily.Monospace) }
            },
        )
    }
}

// ─── Cortex Tab ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CortexTab(
    memories: List<CortexMemoryNode>,
    isLoading: Boolean,
    searchQuery: String,
    stats: CortexStatsResponse?,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: (CortexMemoryNode) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var localQuery by remember { mutableStateOf(searchQuery) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Stats bar
        stats?.let { s ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatChip("${s.total}", "memories")
                StatChip("${s.links}", "links")
                StatChip("${s.episodes}", "episodes")
            }
            Spacer(Modifier.height(8.dp))
        }

        // Search bar
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            placeholder = {
                Text("search memories...", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch(localQuery)
                focusManager.clearFocus()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = ApexBorder,
                cursorColor = Gold,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedPlaceholderColor = TextMuted,
                unfocusedPlaceholderColor = TextMuted,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // Memory list
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
                }
            }
            memories.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (localQuery.isNotBlank()) "no results for \"$localQuery\""
                        else "no cortex memories yet",
                        color = TextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(memories, key = { it.id }) { memory ->
                        CortexMemoryCard(
                            memory = memory,
                            onLongPress = { onDelete(memory) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { localQuery = ""; onRefresh() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = ApexBlack,
                disabledContainerColor = ApexBorder,
            ),
        ) {
            Text("Refresh", fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CortexMemoryCard(
    memory: CortexMemoryNode,
    onLongPress: () -> Unit,
) {
    val agentColor = agentColor(memory.agentId)
    val layerColor = when (memory.layer) {
        "cortex" -> Gold
        "long_term" -> VajraBlue
        "working" -> ElysianViolet
        else -> TextMuted
    }
    val typeIcon = when (memory.memoryType) {
        "episodic" -> "\u23F3"     // hourglass
        "semantic" -> "\u2728"     // sparkles
        "procedural" -> "\u2699"   // gear
        "affective" -> "\u2764"    // heart
        "prospective" -> "\uD83C\uDFAF" // target
        "schematic" -> "\uD83D\uDCD0" // triangle ruler
        else -> "\u25CB"           // circle
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ApexSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ApexBorder, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top row: type icon + agent + layer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(typeIcon, fontSize = 12.sp)
                    Text(
                        memory.memoryType,
                        color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        memory.agentId,
                        color = agentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        memory.layer,
                        color = layerColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Content
            Text(
                memory.content,
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            // Tags
            if (memory.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    memory.tags.take(4).forEach { tag ->
                        Text(
                            "#$tag",
                            color = Gold.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (memory.tags.size > 4) {
                        Text(
                            "+${memory.tags.size - 4}",
                            color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // Bottom row: salience + links + access count
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${(memory.salience * 100).toInt()}% salience",
                    color = if (memory.salience >= 0.7f) Gold else TextMuted,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                )
                if (memory.linkCount > 0) {
                    Text(
                        "${memory.linkCount} links",
                        color = VajraBlue.copy(alpha = 0.7f),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    "${memory.accessCount}x accessed",
                    color = TextMuted,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ─── Agent Tab (Legacy) ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentTab(
    memories: List<AgentMemoryItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDelete: (AgentMemoryItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "AGENT MEMORIES",
            color = Gold.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
                }
            }
            memories.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "no agent memories yet -- keep chatting",
                        color = TextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(memories, key = { it.id }) { memory ->
                        AgentMemoryCard(memory = memory, onLongPress = { onDelete(memory) })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = ApexBlack,
                disabledContainerColor = ApexBorder,
            ),
        ) {
            Text("Refresh", fontFamily = FontFamily.Monospace)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentMemoryCard(
    memory: AgentMemoryItem,
    onLongPress: () -> Unit,
) {
    val color = agentColor(memory.agentId)
    val niceKey = memory.key.replace("_", " ")
        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ApexSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ApexBorder, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(niceKey, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text(memory.value, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(memory.memoryType, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(
                    "${(memory.confidence * 100).toInt()}%",
                    color = if (memory.confidence >= 0.8f) Gold else TextMuted,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ─── Dream Tab ───────────────────────────────────────────────────────

@Composable
private fun DreamTab(
    status: DreamStatusResponse?,
    isTriggering: Boolean,
    onRefresh: () -> Unit,
    onTrigger: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "DREAM ENGINE",
            color = Gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(24.dp))

        if (status == null) {
            CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
        } else {
            // Dream cycle meter
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ApexSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ApexBorder, RoundedCornerShape(12.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val isUnlimited = status.cyclesLimit < 0
                    Text(
                        if (isUnlimited) "${status.cyclesUsed} / \u221E"
                        else "${status.cyclesUsed} / ${status.cyclesLimit}",
                        color = Gold,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "dream cycles this month",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )

                    if (!isUnlimited && status.cyclesLimit > 0) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (status.cyclesUsed.toFloat() / status.cyclesLimit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Gold,
                            trackColor = ApexBorder,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${status.unconsolidatedEpisodes}",
                                color = if (status.unconsolidatedEpisodes > 0) ElysianViolet else TextMuted,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            )
                            Text("episodes", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("pending", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                status.tier.replaceFirstChar { it.uppercase() },
                                color = Gold,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            )
                            Text("tier", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Last report summary
                    status.lastReport?.let { report ->
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = ApexBorder)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "LAST DREAM",
                            color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(4.dp))
                        report.summary?.let {
                            Text(
                                it.take(200),
                                color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Trigger button
            val canDream = status.cyclesLimit < 0 || (status.cyclesLimit > 0 && status.cyclesUsed < status.cyclesLimit)
            Button(
                onClick = onTrigger,
                modifier = Modifier.fillMaxWidth(),
                enabled = canDream && !isTriggering,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElysianViolet,
                    contentColor = ApexBlack,
                    disabledContainerColor = ApexBorder,
                ),
            ) {
                if (isTriggering) {
                    CircularProgressIndicator(
                        color = ApexBlack,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Dreaming...", fontFamily = FontFamily.Monospace)
                } else {
                    Text(
                        if (canDream) "Trigger Dream Cycle"
                        else if (status.cyclesLimit == 0) "Requires Seeker Tier"
                        else "Limit Reached",
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = ApexBlack,
                ),
            ) {
                Text("Refresh", fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ─── Add Memory Dialog ───────────────────────────────────────────────

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (key: String, value: String, type: String) -> Unit,
) {
    val types = listOf("fact", "preference", "context", "relationship")
    var selectedType by remember { mutableStateOf("fact") }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApexSurface,
        titleContentColor = Gold,
        title = { Text("Add Memory", fontFamily = FontFamily.Monospace) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.2f),
                                selectedLabelColor = Gold,
                                containerColor = ApexBlack,
                                labelColor = TextMuted,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Key", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    placeholder = { Text("favorite_color", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = ApexBorder,
                        focusedLabelColor = Gold, unfocusedLabelColor = TextMuted,
                        cursorColor = Gold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text("Value", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    placeholder = { Text("blue", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = ApexBorder,
                        focusedLabelColor = Gold, unfocusedLabelColor = TextMuted,
                        cursorColor = Gold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (key.isNotBlank() && value.isNotBlank()) onSave(key, value, selectedType) },
                enabled = key.isNotBlank() && value.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = Gold),
            ) { Text("Save", fontFamily = FontFamily.Monospace) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
            ) { Text("Cancel", fontFamily = FontFamily.Monospace) }
        },
    )
}

// ─── Helpers ─────────────────────────────────────────────────────────

private fun agentColor(agentId: String) = when (agentId.uppercase()) {
    "AZOTH" -> AzothGold
    "ELYSIAN" -> ElysianViolet
    "VAJRA" -> VajraBlue
    "KETHER" -> KetherWhite
    else -> TextPrimary
}
