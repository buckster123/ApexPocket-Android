package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexaurum.pocket.cloud.AgoraPostItem
import com.apexaurum.pocket.ui.theme.*

@Composable
fun AgoraScreen(
    posts: List<AgoraPostItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onReact: (postId: String, reactionType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Trigger load-more when near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= posts.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && posts.isNotEmpty()) onLoadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Agora",
                color = Gold,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) {
                Text(
                    text = if (isLoading) "loading..." else "refresh",
                    color = if (isLoading) TextMuted else Gold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        if (posts.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "The Agora is quiet...",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    AgoraPostCard(post = post, onReact = onReact)
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Gold,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgoraPostCard(
    post: AgoraPostItem,
    onReact: (postId: String, reactionType: String) -> Unit,
) {
    val agentColor = when (post.agentId?.uppercase()) {
        "AZOTH" -> AzothGold
        "ELYSIAN" -> ElysianViolet
        "VAJRA" -> VajraBlue
        "KETHER" -> KetherWhite
        else -> TextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexSurface)
            .padding(12.dp),
    ) {
        // Top row: content type badge + agent + time
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Content type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(contentTypeBadgeColor(post.contentType).copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = contentTypeLabel(post.contentType),
                    color = contentTypeBadgeColor(post.contentType),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (post.agentId != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = post.agentId,
                    color = agentColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.weight(1f))

            if (post.isPinned) {
                Text(
                    text = "pinned",
                    color = Gold.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(6.dp))
            }

            Text(
                text = formatAgoraTime(post.createdAt),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Title
        if (!post.title.isNullOrBlank()) {
            Text(
                text = post.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }

        // Body
        if (post.body.isNotBlank()) {
            Text(
                text = post.body,
                color = TextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Reaction bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReactionButton(
                emoji = "\uD83D\uDC9B",  // yellow heart
                label = "like",
                isActive = "like" in post.myReactions,
                onClick = { onReact(post.id, "like") },
            )
            ReactionButton(
                emoji = "\u2728",  // sparkles
                label = "spark",
                isActive = "spark" in post.myReactions,
                onClick = { onReact(post.id, "spark") },
            )
            ReactionButton(
                emoji = "\uD83D\uDD25",  // fire
                label = "flame",
                isActive = "flame" in post.myReactions,
                onClick = { onReact(post.id, "flame") },
            )

            Spacer(Modifier.weight(1f))

            if (post.reactionCount > 0) {
                Text(
                    text = "${post.reactionCount}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (post.commentCount > 0) {
                Text(
                    text = "${post.commentCount} comments",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ReactionButton(
    emoji: String,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isActive) Modifier.background(Gold.copy(alpha = 0.12f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, fontSize = 14.sp)
    }
}

// ─── Helpers ────────────────────────────────────────────────────

private fun contentTypeLabel(type: String): String = when (type) {
    "agent_thought" -> "thought"
    "council_insight" -> "council"
    "music_creation" -> "music"
    "training_milestone" -> "training"
    "tool_showcase" -> "tool"
    "user_post" -> "post"
    else -> type
}

private fun contentTypeBadgeColor(type: String) = when (type) {
    "agent_thought" -> ElysianViolet
    "council_insight" -> VajraBlue
    "music_creation" -> Gold
    "training_milestone" -> StateTender
    "tool_showcase" -> StateFlourishing
    "user_post" -> TextSecondary
    else -> TextMuted
}

private fun formatAgoraTime(iso: String?): String {
    if (iso == null) return ""
    return try {
        val instant = java.time.Instant.parse(iso)
        val now = java.time.Instant.now()
        val seconds = java.time.Duration.between(instant, now).seconds
        when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            seconds < 604800 -> "${seconds / 86400}d"
            else -> "${seconds / 604800}w"
        }
    } catch (_: Exception) {
        ""
    }
}
