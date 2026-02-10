package com.apexaurum.pocket

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexaurum.pocket.cloud.*
import com.apexaurum.pocket.soul.Expression
import com.apexaurum.pocket.ui.screens.*
import com.apexaurum.pocket.ui.theme.*

class MainActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get vibrator service
        vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        setContent {
            ApexPocketTheme {
                val vm: PocketViewModel = viewModel()
                val token by vm.token.collectAsStateWithLifecycle()
                val soul by vm.soul.collectAsStateWithLifecycle()
                val messages by vm.messages.collectAsStateWithLifecycle()
                val isChatting by vm.isChatting.collectAsStateWithLifecycle()
                val cloudState by vm.cloudState.collectAsStateWithLifecycle()
                val agents by vm.agents.collectAsStateWithLifecycle()
                val isListening by vm.isListening.collectAsStateWithLifecycle()
                val isSpeaking by vm.isSpeaking.collectAsStateWithLifecycle()
                val autoRead by vm.autoRead.collectAsStateWithLifecycle()
                val pendingVoiceText by vm.pendingVoiceText.collectAsStateWithLifecycle()
                val memories by vm.memories.collectAsStateWithLifecycle()
                val memoriesLoading by vm.memoriesLoading.collectAsStateWithLifecycle()
                val agoraPosts by vm.agoraPosts.collectAsStateWithLifecycle()
                val agoraLoading by vm.agoraLoading.collectAsStateWithLifecycle()
                val villageEvents by vm.villageEvents.collectAsStateWithLifecycle()
                val villagePulseConnected by vm.villagePulseConnected.collectAsStateWithLifecycle()
                val unseenPulseCount by vm.unseenPulseCount.collectAsStateWithLifecycle()
                val latestTickerEvent by vm.latestTickerEvent.collectAsStateWithLifecycle()
                val expressionOverride by vm.expressionOverride.collectAsStateWithLifecycle()
                val councilSessions by vm.councilSessions.collectAsStateWithLifecycle()
                val councilDetail by vm.councilDetail.collectAsStateWithLifecycle()
                val councilAgentOutputs by vm.councilAgentOutputs.collectAsStateWithLifecycle()
                val councilCurrentRound by vm.councilCurrentRound.collectAsStateWithLifecycle()
                val councilStreaming by vm.councilStreaming.collectAsStateWithLifecycle()
                val councilButtInSent by vm.councilButtInSent.collectAsStateWithLifecycle()
                val micAvailable = remember { vm.speechService.isRecognitionAvailable() }

                // Request notification permission on Android 13+
                if (Build.VERSION.SDK_INT >= 33) {
                    val notifLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { _ -> }
                    LaunchedEffect(Unit) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (token == null) {
                    // Not paired — show pairing screen
                    PairScreen(
                        onPair = { vm.pair(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ApexBlack)
                            .systemBarsPadding(),
                    )
                } else {
                    // Paired — main app
                    MainScreen(
                        vm = vm,
                        soul = soul,
                        messages = messages,
                        isChatting = isChatting,
                        cloudState = cloudState,
                        agents = agents,
                        memories = memories,
                        memoriesLoading = memoriesLoading,
                        agoraPosts = agoraPosts,
                        agoraLoading = agoraLoading,
                        villageEvents = villageEvents,
                        villagePulseConnected = villagePulseConnected,
                        unseenPulseCount = unseenPulseCount,
                        latestTickerEvent = latestTickerEvent,
                        expressionOverride = expressionOverride,
                        councilSessions = councilSessions,
                        councilDetail = councilDetail,
                        councilAgentOutputs = councilAgentOutputs,
                        councilCurrentRound = councilCurrentRound,
                        councilStreaming = councilStreaming,
                        councilButtInSent = councilButtInSent,
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        autoRead = autoRead,
                        pendingVoiceText = pendingVoiceText,
                        micAvailable = micAvailable,
                        onVibrate = { pattern -> vibrate(pattern) },
                    )
                }
            }
        }
    }

    private fun vibrate(pattern: VibratePattern) {
        val v = vibrator ?: return
        val effect = when (pattern) {
            VibratePattern.LOVE -> VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1)
            VibratePattern.POKE -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            VibratePattern.SYNC -> VibrationEffect.createWaveform(longArrayOf(0, 50, 30, 50, 30, 50), -1)
        }
        v.vibrate(effect)
    }
}

enum class VibratePattern { LOVE, POKE, SYNC }

private data class TabItem(val title: String, val icon: ImageVector)

/** The tab-based main screen shown after pairing. */
@Composable
private fun MainScreen(
    vm: PocketViewModel,
    soul: com.apexaurum.pocket.soul.SoulData,
    messages: List<ChatMessage>,
    isChatting: Boolean,
    cloudState: CloudState,
    agents: List<com.apexaurum.pocket.cloud.AgentInfo>,
    memories: List<com.apexaurum.pocket.cloud.AgentMemoryItem>,
    memoriesLoading: Boolean,
    agoraPosts: List<com.apexaurum.pocket.cloud.AgoraPostItem>,
    agoraLoading: Boolean,
    villageEvents: List<VillageEvent>,
    villagePulseConnected: Boolean,
    unseenPulseCount: Int,
    latestTickerEvent: VillageEvent?,
    expressionOverride: Expression?,
    councilSessions: List<CouncilSession>,
    councilDetail: CouncilSessionDetail?,
    councilAgentOutputs: Map<String, String>,
    councilCurrentRound: Int,
    councilStreaming: Boolean,
    councilButtInSent: Boolean,
    isListening: Boolean,
    isSpeaking: Boolean,
    autoRead: Boolean,
    pendingVoiceText: String?,
    micAvailable: Boolean,
    onVibrate: (VibratePattern) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var pulseNav by remember { mutableStateOf("events") }
    var selectedCouncilId by remember { mutableStateOf<String?>(null) }

    // Reset pulse sub-nav when switching away from Pulse tab
    LaunchedEffect(selectedTab) {
        if (selectedTab != 3) pulseNav = "events"
    }

    // Back handler for council sub-navigation
    BackHandler(selectedTab == 3 && pulseNav != "events") {
        when (pulseNav) {
            "council_detail" -> {
                vm.clearCouncilDetail()
                pulseNav = "council_list"
            }
            else -> pulseNav = "events"
        }
    }

    val tabs = listOf(
        TabItem("Face", Icons.Default.Face),
        TabItem("Chat", Icons.Default.ChatBubbleOutline),
        TabItem("Agora", Icons.Default.Forum),
        TabItem("Pulse", Icons.Default.FavoriteBorder),
        TabItem("Memories", Icons.Default.AutoAwesome),
        TabItem("Status", Icons.Default.Info),
    )

    // Village Pulse lifecycle — connect on resume, disconnect on pause
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, cloudState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (cloudState == CloudState.CONNECTED) vm.connectVillagePulse()
                }
                Lifecycle.Event.ON_PAUSE -> vm.disconnectVillagePulse()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = ApexBlack,
        bottomBar = {
            NavigationBar(
                containerColor = ApexDarkSurface,
                contentColor = Gold,
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 3) vm.clearUnseenPulse()
                        },
                        icon = {
                            if (index == 3 && unseenPulseCount > 0 && selectedTab != 3) {
                                BadgedBox(badge = {
                                    Badge(containerColor = Gold, contentColor = ApexBlack) {
                                        Text(if (unseenPulseCount > 9) "9+" else "$unseenPulseCount")
                                    }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = {
                            Text(
                                tab.title,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold,
                            selectedTextColor = Gold,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Gold.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding(),
        ) {
            when (selectedTab) {
                0 -> FaceScreen(
                    soul = soul,
                    onLove = {
                        vm.love()
                        onVibrate(VibratePattern.LOVE)
                    },
                    onPoke = {
                        vm.poke()
                        onVibrate(VibratePattern.POKE)
                    },
                    latestVillageEvent = latestTickerEvent,
                    expressionOverride = expressionOverride,
                )
                1 -> ChatScreen(
                    soul = soul,
                    messages = messages,
                    isChatting = isChatting,
                    onSend = { vm.sendMessage(it) },
                    agents = agents,
                    onSelectAgent = { vm.selectAgent(it) },
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    autoRead = autoRead,
                    pendingVoiceText = pendingVoiceText,
                    onToggleListening = { vm.toggleListening() },
                    onToggleAutoRead = { vm.toggleAutoRead() },
                    onStopSpeaking = { vm.stopSpeaking() },
                    onClearPendingVoice = { vm.clearPendingVoiceText() },
                    micAvailable = micAvailable,
                    onRemember = { vm.rememberMessage(it) },
                    onRegenerate = { vm.regenerateLastResponse() },
                    onSendWithImage = { text, img -> vm.sendMessageWithImage(text, img) },
                )
                2 -> AgoraScreen(
                    posts = agoraPosts,
                    isLoading = agoraLoading,
                    onRefresh = { vm.loadAgoraFeed() },
                    onLoadMore = { vm.loadMoreAgora() },
                    onReact = { postId, type -> vm.toggleReaction(postId, type) },
                )
                3 -> when (pulseNav) {
                    "council_list" -> CouncilListScreen(
                        sessions = councilSessions,
                        onBack = { pulseNav = "events" },
                        onSessionClick = { id ->
                            selectedCouncilId = id
                            vm.loadCouncilSession(id)
                            pulseNav = "council_detail"
                        },
                        onRefresh = { vm.fetchCouncilSessions() },
                    )
                    "council_detail" -> CouncilDetailScreen(
                        session = councilDetail,
                        agentOutputs = councilAgentOutputs,
                        currentRound = councilCurrentRound,
                        isStreaming = councilStreaming,
                        buttInSent = councilButtInSent,
                        onBack = {
                            vm.clearCouncilDetail()
                            pulseNav = "council_list"
                        },
                        onButtIn = { msg ->
                            selectedCouncilId?.let { vm.submitButtIn(it, msg) }
                        },
                    )
                    else -> PulseScreen(
                        events = villageEvents,
                        isConnected = villagePulseConnected,
                        onCouncilsClick = {
                            vm.fetchCouncilSessions()
                            pulseNav = "council_list"
                        },
                    )
                }
                4 -> MemoriesScreen(
                    memories = memories,
                    isLoading = memoriesLoading,
                    onRefresh = { vm.fetchMemories() },
                    onSave = { k, v, t -> vm.saveMemory(k, v, t) },
                    onDelete = { vm.deleteMemory(it) },
                )
                5 -> StatusScreen(
                    soul = soul,
                    cloudState = cloudState,
                    onSync = {
                        vm.syncToCloud()
                        onVibrate(VibratePattern.SYNC)
                    },
                    onUnpair = { vm.unpair() },
                )
            }
        }
    }
}
