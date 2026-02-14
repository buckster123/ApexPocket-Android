package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.CortexGraphEdge
import com.apexaurum.pocket.cloud.CortexGraphResponse
import com.apexaurum.pocket.cloud.CortexMemoryNode
import com.apexaurum.pocket.cloud.CortexNeighborItem
import com.apexaurum.pocket.ui.theme.*
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.*

// ── Physics Node ──

private class GraphNode(
    val data: CortexMemoryNode,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
) {
    val radius: Float get() = 8f + (data.salience * 16f) // 8-24dp
}

// ── Link type → color ──

private fun linkTypeColor(type: String): Color = when (type) {
    "semantic" -> VajraBlue
    "causal" -> Gold
    "affective" -> ElysianViolet
    "temporal" -> AzothGold.copy(alpha = 0.7f)
    "contextual" -> TextMuted
    "supports" -> Color(0xFF66BB6A)      // green
    "contradicts" -> Color(0xFFEF5350)   // red
    "derived_from" -> KetherWhite.copy(alpha = 0.5f)
    "part_of" -> VajraBlue.copy(alpha = 0.6f)
    else -> TextMuted
}

private fun agentNodeColor(agentId: String): Color = when (agentId.uppercase()) {
    "AZOTH" -> AzothGold
    "ELYSIAN" -> ElysianViolet
    "VAJRA" -> VajraBlue
    "KETHER" -> KetherWhite
    else -> TextPrimary
}

// ── Force-Directed Graph Composable ──

@Composable
fun MemoryGraphView(
    graphData: CortexGraphResponse?,
    isLoading: Boolean,
    selectedNode: CortexMemoryNode?,
    neighbors: List<CortexNeighborItem>,
    onFetchGraph: () -> Unit,
    onSelectNode: (CortexMemoryNode) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-fetch on first composition
    LaunchedEffect(Unit) { onFetchGraph() }

    if (isLoading && graphData == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text("loading graph...", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        return
    }

    val nodes = graphData?.nodes ?: emptyList()
    val edges = graphData?.edges ?: emptyList()

    if (nodes.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("no graph data", color = TextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onFetchGraph,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = ApexBlack),
                ) { Text("Refresh", fontFamily = FontFamily.Monospace) }
            }
        }
        return
    }

    // Build physics nodes — spiral initial placement
    val graphNodes = remember(nodes) {
        nodes.mapIndexed { i, node ->
            val angle = i * 2.4f // golden angle
            val r = 30f + i * 3f
            GraphNode(
                data = node,
                x = r * cos(angle),
                y = r * sin(angle),
            )
        }
    }

    // Build edge index for quick lookup
    val edgePairs = remember(edges, graphNodes) {
        val idMap = graphNodes.associateBy { it.data.id }
        edges.mapNotNull { e ->
            val src = idMap[e.source]
            val tgt = idMap[e.target]
            if (src != null && tgt != null) Triple(src, tgt, e) else null
        }
    }

    // Pan + zoom state
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var settled by remember { mutableStateOf(false) }

    // Physics simulation
    LaunchedEffect(graphNodes) {
        settled = false
        var frame = 0
        while (!settled && frame < 300) {
            awaitFrame()
            frame++

            val dt = 0.016f // ~60fps target
            var totalKE = 0f

            // Repulsion (all pairs)
            for (i in graphNodes.indices) {
                for (j in i + 1 until graphNodes.size) {
                    val a = graphNodes[i]
                    val b = graphNodes[j]
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val force = 2000f / (dist * dist)
                    val fx = force * dx / dist
                    val fy = force * dy / dist
                    a.vx -= fx * dt
                    a.vy -= fy * dt
                    b.vx += fx * dt
                    b.vy += fy * dt
                }
            }

            // Attraction (along edges)
            for ((src, tgt, edge) in edgePairs) {
                val dx = tgt.x - src.x
                val dy = tgt.y - src.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val idealDist = 80f
                val force = (dist - idealDist) * 0.01f * edge.weight
                val fx = force * dx / dist
                val fy = force * dy / dist
                src.vx += fx * dt
                src.vy += fy * dt
                tgt.vx -= fx * dt
                tgt.vy -= fy * dt
            }

            // Center gravity + damping + integrate
            for (node in graphNodes) {
                node.vx -= node.x * 0.001f  // center pull
                node.vy -= node.y * 0.001f
                node.vx *= 0.85f  // damping
                node.vy *= 0.85f
                node.x += node.vx
                node.y += node.vy
                totalKE += node.vx * node.vx + node.vy * node.vy
            }

            if (totalKE < 0.1f && frame > 30) {
                settled = true
            }
        }
        settled = true
    }

    // Text measurer for labels
    val textMeasurer = rememberTextMeasurer()

    Box(modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexBlack)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
                        panX += pan.x
                        panY += pan.y
                    }
                }
                .pointerInput(graphNodes) {
                    detectTapGestures { tapOffset ->
                        val cx = size.width / 2f + panX
                        val cy = size.height / 2f + panY
                        // Hit test nodes (reverse order = top first)
                        val hit = graphNodes.lastOrNull { node ->
                            val sx = cx + node.x * scale
                            val sy = cy + node.y * scale
                            val dist = sqrt(
                                (tapOffset.x - sx).pow(2) + (tapOffset.y - sy).pow(2)
                            )
                            dist < (node.radius * scale + 12f)
                        }
                        if (hit != null) onSelectNode(hit.data) else onClearSelection()
                    }
                }
        ) {
            val cx = size.width / 2f + panX
            val cy = size.height / 2f + panY

            // 1. Draw edges
            for ((src, tgt, edge) in edgePairs) {
                val start = Offset(cx + src.x * scale, cy + src.y * scale)
                val end = Offset(cx + tgt.x * scale, cy + tgt.y * scale)
                // Cull offscreen
                if (start.x < -100 && end.x < -100) continue
                if (start.y < -100 && end.y < -100) continue
                if (start.x > size.width + 100 && end.x > size.width + 100) continue
                if (start.y > size.height + 100 && end.y > size.height + 100) continue

                drawLine(
                    color = linkTypeColor(edge.type).copy(alpha = 0.15f + edge.weight * 0.25f),
                    start = start,
                    end = end,
                    strokeWidth = (1f + edge.weight * 2f) * scale.coerceIn(0.5f, 1.5f),
                )
            }

            // 2. Draw nodes
            for (node in graphNodes) {
                val sx = cx + node.x * scale
                val sy = cy + node.y * scale
                // Cull offscreen
                if (sx < -50 || sy < -50 || sx > size.width + 50 || sy > size.height + 50) continue

                val r = node.radius * scale
                val color = agentNodeColor(node.data.agentId)
                val isSelected = selectedNode?.id == node.data.id

                // Glow
                drawCircle(
                    color = color.copy(alpha = 0.15f),
                    radius = r * 1.8f,
                    center = Offset(sx, sy),
                    blendMode = BlendMode.Screen,
                )

                // Fill
                drawCircle(
                    color = color.copy(alpha = 0.8f),
                    radius = r,
                    center = Offset(sx, sy),
                )

                // Selected ring
                if (isSelected) {
                    drawCircle(
                        color = Gold,
                        radius = r + 4f * scale,
                        center = Offset(sx, sy),
                        style = Stroke(width = 2f * scale),
                    )
                }

                // Labels (only when zoomed in enough)
                if (scale > 0.6f) {
                    val label = node.data.content.take(20).replace("\n", " ")
                    val textResult = textMeasurer.measure(
                        AnnotatedString(label),
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    drawText(
                        textResult,
                        topLeft = Offset(
                            sx - textResult.size.width / 2f,
                            sy + r + 4f * scale,
                        ),
                    )
                }
            }
        }

        // Bottom info: node/edge count
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Text(
                "${nodes.size}n ${edges.size}e",
                color = TextMuted.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Refresh button
        Button(
            onClick = onFetchGraph,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold.copy(alpha = 0.8f),
                contentColor = ApexBlack,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("Refresh", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        // Selected node detail overlay
        selectedNode?.let { node ->
            NodeDetailOverlay(
                node = node,
                neighbors = neighbors,
                onDismiss = onClearSelection,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ── Node Detail Overlay ──

@Composable
private fun NodeDetailOverlay(
    node: CortexMemoryNode,
    neighbors: List<CortexNeighborItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val agentColor = agentNodeColor(node.agentId)

    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = ApexSurface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .border(
                width = 1.dp,
                color = agentColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: agent + type + layer
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            node.agentId,
                            color = agentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            node.memoryType,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            node.layer,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        "${(node.salience * 100).toInt()}%",
                        color = if (node.salience >= 0.7f) Gold else TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Content preview
            item {
                Text(
                    node.content.take(200),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                )
            }

            // Tags
            if (node.tags.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        node.tags.take(6).forEach { tag ->
                            Text(
                                "#$tag",
                                color = Gold.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            // Stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${node.linkCount} links",
                        color = VajraBlue.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "${node.accessCount}x accessed",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Neighbors
            if (neighbors.isNotEmpty()) {
                item {
                    HorizontalDivider(color = ApexBorder)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "NEIGHBORS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                items(neighbors.take(5)) { neighbor ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            neighbor.linkType,
                            color = linkTypeColor(neighbor.linkType).copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(72.dp),
                        )
                        Text(
                            neighbor.content.take(60).replace("\n", " "),
                            color = TextPrimary.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
