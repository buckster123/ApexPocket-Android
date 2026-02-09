package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.MemoryItem
import com.apexaurum.pocket.ui.theme.*

@Composable
fun MemoriesScreen(
    memories: List<MemoryItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
                    items(memories) { memory ->
                        MemoryCard(memory)
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

@Composable
private fun MemoryCard(memory: MemoryItem) {
    val agentColor = when (memory.agent.uppercase()) {
        "AZOTH" -> AzothGold
        "ELYSIAN" -> ElysianViolet
        "VAJRA" -> VajraBlue
        "KETHER" -> KetherWhite
        else -> TextPrimary
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ApexSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ApexBorder, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                memory.content,
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
                    memory.agent,
                    color = agentColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    memory.age,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
