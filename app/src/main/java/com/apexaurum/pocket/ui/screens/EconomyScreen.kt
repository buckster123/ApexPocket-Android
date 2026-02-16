package com.apexaurum.pocket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.apexaurum.pocket.cloud.*
import com.apexaurum.pocket.ui.theme.*

@Composable
fun EconomyScreen(
    balance: AJBalanceResponse?,
    leaderboard: AJLeaderboardResponse?,
    shop: AJShopResponse?,
    transactions: AJTransactionsResponse?,
    marketplaceListings: MarketplaceListingsResponse?,
    marketplaceLoading: Boolean,
    ajLoading: Boolean,
    ajFeedback: String?,
    userTier: String,
    onRefreshBalance: () -> Unit,
    onRefreshLeaderboard: () -> Unit,
    onRefreshShop: () -> Unit,
    onRefreshTransactions: () -> Unit,
    onPurchase: (item: String, quantity: Int, entityId: String?) -> Unit,
    onTip: (agentId: String, amount: Float) -> Unit,
    onActivateCitizen: () -> Unit,
    onSubscribe: (tier: String) -> Unit,
    onFetchMarketplace: (search: String?) -> Unit,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf("leaderboard") }
    var tipTarget by remember { mutableStateOf<String?>(null) }

    // Snackbar for feedback
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(ajFeedback) {
        if (ajFeedback != null) {
            snackbarHost.showSnackbar(ajFeedback)
            onClearFeedback()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ApexBorder,
                    modifier = Modifier.clickable(onClick = onBack),
                ) {
                    Text(
                        "Back",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "ApexJoule Economy",
                    color = Gold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Citizen activation banner
            if (userTier == "free_trial") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Gold.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Activate AJ Citizen",
                                color = Gold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Unlock the economy + 100 AJ welcome bonus",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onActivateCitizen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = ApexBlack,
                            ),
                        ) {
                            Text("Activate", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }

            // View selector pills
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("leaderboard" to "Leaderboard", "shop" to "Shop", "subscribe" to "Subscribe", "marketplace" to "Marketplace").forEach { (key, label) ->
                    val selected = view == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) Gold.copy(alpha = 0.2f) else ApexSurface,
                        modifier = Modifier.clickable {
                            view = key
                            when (key) {
                                "leaderboard" -> { onRefreshBalance(); onRefreshLeaderboard() }
                                "shop" -> onRefreshShop()
                                "marketplace" -> onFetchMarketplace(null)
                            }
                        },
                    ) {
                        Text(
                            label,
                            color = if (selected) Gold else TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Content
            when (view) {
                "leaderboard" -> LeaderboardView(
                    balance = balance,
                    leaderboard = leaderboard,
                    isLoading = ajLoading,
                    onTipAgent = { tipTarget = it },
                )
                "shop" -> ShopView(
                    shop = shop,
                    userBalance = balance?.user?.balance ?: 0f,
                    isLoading = ajLoading,
                    onPurchase = onPurchase,
                )
                "subscribe" -> SubscribeView(
                    userBalance = balance?.user?.balance ?: 0f,
                    currentTier = userTier,
                    isLoading = ajLoading,
                    onSubscribe = onSubscribe,
                )
                "marketplace" -> MarketplaceView(
                    listings = marketplaceListings,
                    isLoading = marketplaceLoading,
                    onSearch = onFetchMarketplace,
                )
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            snackbar = { data ->
                Snackbar(snackbarData = data, containerColor = ApexSurface, contentColor = Gold)
            },
        )

        // Tip dialog
        if (tipTarget != null) {
            TipDialog(
                agentId = tipTarget!!,
                userBalance = balance?.user?.balance ?: 0f,
                onTip = { amount -> onTip(tipTarget!!, amount); tipTarget = null },
                onDismiss = { tipTarget = null },
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// LEADERBOARD VIEW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LeaderboardView(
    balance: AJBalanceResponse?,
    leaderboard: AJLeaderboardResponse?,
    isLoading: Boolean,
    onTipAgent: (String) -> Unit,
) {
    if (isLoading && balance == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // User summary card
        balance?.user?.let { user ->
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Gold.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Your Balance",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                user.levelName ?: "Ember",
                                color = Gold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "%.1f AJ".format(user.balance),
                            color = Gold,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                "earned: %.0f".format(user.totalEarned),
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "spent: %.0f".format(user.totalSpent),
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            if (user.loveDepth != null) {
                                Text(
                                    "love: ${user.loveDepth}",
                                    color = ElysianViolet,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Agent leaderboard
        val entries = leaderboard?.agents ?: emptyList()
        items(entries) { entry ->
            val color = agentColor(entry.agentId)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ApexSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Agent color dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.agentId,
                                color = color,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = color.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "Lv${entry.level}",
                                    color = color,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            if (entry.loveDepthTier != null) {
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ElysianViolet.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        entry.loveDepthTier,
                                        color = ElysianViolet,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "%.1f AJ  |  earned: %.0f".format(entry.balance, entry.totalEarned),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    // Tip button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Gold.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { onTipAgent(entry.agentId) },
                    ) {
                        Text(
                            "Tip",
                            color = Gold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        if (entries.isEmpty() && !isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No agents yet", color = TextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// SHOP VIEW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ShopView(
    shop: AJShopResponse?,
    userBalance: Float,
    isLoading: Boolean,
    onPurchase: (item: String, quantity: Int, entityId: String?) -> Unit,
) {
    if (isLoading && shop == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
        }
        return
    }

    val prices = shop?.prices ?: emptyMap()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(prices.entries.toList()) { (item, price) ->
            val canAfford = userBalance >= price
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ApexSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        shopItemLabel(item),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$price AJ",
                        color = Gold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPurchase(item, 1, null) },
                        enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = ApexBlack,
                            disabledContainerColor = ApexBorder,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Buy", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// MARKETPLACE VIEW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MarketplaceView(
    listings: MarketplaceListingsResponse?,
    isLoading: Boolean,
    onSearch: (String?) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }

    Column {
        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it; onSearch(it.ifBlank { null }) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = {
                Text("Search marketplace...", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = ApexBorder,
                cursorColor = Gold,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            singleLine = true,
        )

        if (isLoading && listings == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
            }
            return
        }

        val items = listings?.listings ?: emptyList()
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No listings yet", color = TextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(items) { listing ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ApexSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                listing.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (listing.priceAj > 0) "%.0f AJ".format(listing.priceAj) else "Free",
                                color = Gold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (listing.description != null) {
                            Text(
                                listing.description,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (listing.rating != null) {
                                Text(
                                    "%.1f (%d)".format(listing.rating, listing.ratingCount),
                                    color = StateWarm,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Text(
                                "${listing.downloads} downloads",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            listing.tags.take(3).forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = VajraBlue.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        tag,
                                        color = VajraBlue,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// TIP DIALOG
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TipDialog(
    agentId: String,
    userBalance: Float,
    onTip: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(5f, 10f, 25f, 50f, 100f)
    val color = agentColor(agentId)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApexDarkSurface,
        title = {
            Text(
                "Tip $agentId",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    "Your balance: %.1f AJ".format(userBalance),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    presets.forEach { amount ->
                        val canAfford = userBalance >= amount
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (canAfford) Gold.copy(alpha = 0.15f) else ApexBorder,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = canAfford) { onTip(amount) },
                        ) {
                            Text(
                                "%.0f".format(amount),
                                color = if (canAfford) Gold else TextMuted,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontFamily = FontFamily.Monospace)
            }
        },
    )
}


// ═══════════════════════════════════════════════════════════════════════════════
// SUBSCRIBE VIEW — Pay for tiers with AJ
// ═══════════════════════════════════════════════════════════════════════════════

private val AJ_TIERS = listOf(
    Triple("seeker", "Seeker", 8_000),
    Triple("adept", "Adept", 24_000),
    Triple("opus", "Opus", 80_000),
    Triple("azothic", "Azothic", 240_000),
)

@Composable
private fun SubscribeView(
    userBalance: Float,
    currentTier: String,
    isLoading: Boolean,
    onSubscribe: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Text(
                "Subscribe with AJ",
                color = Gold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "Pay for your tier with ApexJoule credits. 30-day period.",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        items(AJ_TIERS.size) { index ->
            val (tierId, tierName, price) = AJ_TIERS[index]
            val isCurrent = currentTier == tierId
            val canAfford = userBalance >= price
            val tierColor = when (tierId) {
                "seeker" -> TextPrimary
                "adept" -> VajraBlue
                "opus" -> ElysianViolet
                "azothic" -> Gold
                else -> TextPrimary
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) tierColor.copy(alpha = 0.08f) else ApexSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tierName,
                                color = tierColor,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = tierColor.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        "Current",
                                        color = tierColor,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "%,d AJ / 30 days".format(price),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (!isCurrent) {
                        Button(
                            onClick = { onSubscribe(tierId) },
                            enabled = canAfford && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = ApexBlack,
                                disabledContainerColor = ApexBorder,
                            ),
                        ) {
                            Text(
                                if (canAfford) "Subscribe" else "Need AJ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }

        // Balance reminder
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Gold.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    "Your balance: %.0f AJ".format(userBalance),
                    color = Gold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

private fun shopItemLabel(item: String): String = when (item) {
    "message_haiku" -> "Haiku Message"
    "message_sonnet" -> "Sonnet Message"
    "message_opus" -> "Opus Message"
    "tool_call" -> "Tool Call"
    "music_generate" -> "Music Generation"
    "council_round" -> "Council Round"
    "council_session" -> "Council Session"
    "file_upload_mb" -> "File Upload (1 MB)"
    "memory_store" -> "Memory Store"
    "nursery_train" -> "Training Job"
    "quest_bounty" -> "Quest Bounty"
    else -> item.replace("_", " ").replaceFirstChar { it.uppercase() }
}
