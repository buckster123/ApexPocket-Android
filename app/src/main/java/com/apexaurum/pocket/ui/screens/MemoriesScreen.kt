package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.AgentMemoryItem
import com.apexaurum.pocket.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoriesScreen(
    memories: List<AgentMemoryItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSave: (key: String, value: String, type: String) -> Unit,
    onDelete: (memoryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AgentMemoryItem?>(null) }

    Scaffold(
        containerColor = ApexBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Gold,
                contentColor = ApexBlack,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        ) {
            Text(
                "MEMORIES",
                color = Gold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(16.dp))

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
                            "no memories yet -- keep chatting",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(memories, key = { it.id }) { memory ->
                            MemoryCard(
                                memory = memory,
                                onLongPress = { deleteTarget = memory },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

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

    // Add Memory Dialog
    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { key, value, type ->
                onSave(key, value, type)
                showAddDialog = false
            },
        )
    }

    // Delete Confirmation Dialog
    deleteTarget?.let { memory ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = ApexSurface,
            titleContentColor = Gold,
            textContentColor = TextPrimary,
            title = {
                Text(
                    "Delete Memory",
                    fontFamily = FontFamily.Monospace,
                )
            },
            text = {
                Text(
                    "Delete \"${memory.key}\"?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(memory.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Gold),
                ) {
                    Text("Delete", fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
                ) {
                    Text("Cancel", fontFamily = FontFamily.Monospace)
                }
            },
        )
    }
}

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
        title = {
            Text("Add Memory", fontFamily = FontFamily.Monospace)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    types.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = {
                                Text(
                                    type,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            },
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
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    placeholder = { Text("favorite_color", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = ApexBorder,
                        focusedLabelColor = Gold,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = Gold,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    placeholder = { Text("blue", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = ApexBorder,
                        focusedLabelColor = Gold,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = Gold,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
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
            ) {
                Text("Save", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
            ) {
                Text("Cancel", fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryCard(
    memory: AgentMemoryItem,
    onLongPress: () -> Unit,
) {
    val agentColor = when (memory.agentId.uppercase()) {
        "AZOTH" -> AzothGold
        "ELYSIAN" -> ElysianViolet
        "VAJRA" -> VajraBlue
        "KETHER" -> KetherWhite
        else -> TextPrimary
    }

    // Format key nicely: snake_case -> Title Case
    val niceKey = memory.key.replace("_", " ")
        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

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
            Text(
                niceKey,
                color = agentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                memory.value,
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    memory.memoryType,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                // Confidence badge
                Text(
                    "${(memory.confidence * 100).toInt()}%",
                    color = if (memory.confidence >= 0.8f) Gold else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
