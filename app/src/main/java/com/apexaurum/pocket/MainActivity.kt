package com.apexaurum.pocket

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    isListening: Boolean,
    isSpeaking: Boolean,
    autoRead: Boolean,
    pendingVoiceText: String?,
    micAvailable: Boolean,
    onVibrate: (VibratePattern) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        TabItem("Face", Icons.Default.Face),
        TabItem("Chat", Icons.Default.ChatBubbleOutline),
        TabItem("Memories", Icons.Default.AutoAwesome),
        TabItem("Status", Icons.Default.Info),
    )

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
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
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
                )
                2 -> MemoriesScreen(
                    memories = memories,
                    isLoading = memoriesLoading,
                    onRefresh = { vm.fetchMemories() },
                    onSave = { k, v, t -> vm.saveMemory(k, v, t) },
                    onDelete = { vm.deleteMemory(it) },
                )
                3 -> StatusScreen(
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
