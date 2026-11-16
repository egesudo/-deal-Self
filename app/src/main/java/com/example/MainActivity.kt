package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.CompanionAiService
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.auth.signOutUser
import com.example.data.repository.CompanionRepository
import com.example.telemetry.ProductionDiagnosticLogger
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CharacterScreen
import com.example.ui.screens.CharacterSubSection
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingFlowScreen
import com.example.ui.screens.SettingsAndPrivacySheet
import com.example.ui.screens.TextConversationScreen
import com.example.ui.screens.VoiceSessionScreen
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalOnToggleLanguage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.tr
import com.example.viewmodel.CompanionViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install PII-redacted production crash & diagnostic breadcrumb handler
        ProductionDiagnosticLogger.install()

        // STEP 1: Configure App Check with launch intent before any UI, ViewModel, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                IdealSelfRootApp()
            }
        }
    }
}

// Primary Bottom Navigation Destinations (Strictly limited to Home and Character per minimal UX architecture)
enum class PrimaryTab(val label: String) {
    HOME("Home"),
    CHARACTER("Character")
}

// Focused Overlay Modes (Full-screen Voice Session & Focused Text Conversation)
enum class ActiveOverlay {
    NONE,
    VOICE_SESSION,
    TEXT_CONVERSATION
}

@Composable
fun IdealSelfRootApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    var isGuestSession by rememberSaveable { mutableStateOf(false) }
    var appLanguage by rememberSaveable { mutableStateOf(AppLanguage.TR) }

    CompositionLocalProvider(
        LocalAppLanguage provides appLanguage,
        LocalOnToggleLanguage provides { appLanguage = appLanguage.toggled() }
    ) {
        if (user == null && !isGuestSession) {
            AuthScreen(
                onAuthSuccess = {},
                onContinueAsGuest = { isGuestSession = true }
            )
        } else {
            val activeUserId = user?.uid ?: CompanionRepository.GUEST_USER_ID
            val activeDisplayName = user?.displayName ?: if (appLanguage == AppLanguage.TR) {
                "Misafir Test Kullanıcısı"
            } else {
                "Guest Tester"
            }
            val activeEmail = user?.email.orEmpty().ifBlank { "guest@idealself.local" }

            key(activeUserId) {
                AuthenticatedCompanionShell(
                    currentUserId = activeUserId,
                    displayName = activeDisplayName,
                    email = activeEmail,
                    onExitGuestSession = { isGuestSession = false }
                )
            }
        }
    }
}

@Composable
fun AuthenticatedCompanionShell(
    currentUserId: String,
    displayName: String,
    email: String,
    onExitGuestSession: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val appLanguage = LocalAppLanguage.current
    val isGuestUser = currentUserId == CompanionRepository.GUEST_USER_ID

    val viewModel: CompanionViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                CompanionViewModel(
                    repository = CompanionRepository(context = app, isGuestMode = isGuestUser),
                    aiService = CompanionAiService(),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email
                )
            }
        }
    )

    val companionState by viewModel.companionState.collectAsStateWithLifecycle()
    val isProfileLoaded by viewModel.isProfileLoaded.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val aiTurnState by viewModel.aiTurnState.collectAsStateWithLifecycle()
    val activeEmotion by viewModel.activeEmotion.collectAsStateWithLifecycle()
    val visualState by viewModel.visualState.collectAsStateWithLifecycle()
    val pendingProposal by viewModel.pendingDiscoveryProposal.collectAsStateWithLifecycle()
    val lastExtractedMemory by viewModel.lastExtractedMemory.collectAsStateWithLifecycle()
    val isSearchingWeb by viewModel.isSearchingWeb.collectAsStateWithLifecycle()
    val lastWebGroundedSources by viewModel.lastWebGroundedSources.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val isQuizSynthesizing by viewModel.isQuizSynthesizing.collectAsStateWithLifecycle()
    val usageSnapshot by viewModel.usageSnapshot.collectAsStateWithLifecycle()
    val quotaConfig by viewModel.quotaConfig.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(PrimaryTab.HOME) }
    var characterSubSection by rememberSaveable { mutableStateOf(CharacterSubSection.APPEARANCE) }
    var activeOverlay by rememberSaveable { mutableStateOf(ActiveOverlay.NONE) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner, errorBanner) {
        val msg = errorBanner ?: statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanners()
        }
    }

    if (isProfileLoaded && !companionState.userAccount.onboardingCompleted) {
        OnboardingFlowScreen(
            companionState = companionState,
            isQuizSynthesizing = isQuizSynthesizing,
            pendingProposal = pendingProposal,
            onSaveDiscoveryProgress = { dims ->
                viewModel.saveDiscoveryProgress(dims)
            },
            onSynthesizeDiscovery = { dims ->
                viewModel.synthesizeDiscoveryDimensions(dims)
            },
            onApproveProposal = { approved ->
                viewModel.approveDiscoveryProposal(approved)
            },
            onDiscardProposal = {
                viewModel.discardDiscoveryProposal()
            },
            onCompleteDirectCreation = { name, archetype, aspiration, pillars, appearance, clothing ->
                viewModel.completeDirectCreation(
                    companionName = name,
                    archetypeTitle = archetype,
                    coreAspiration = aspiration,
                    lifePillars = pillars,
                    appearance = appearance,
                    clothing = clothing
                )
            },
            onSkipOnboarding = {
                viewModel.skipOnboarding()
            }
        )
        return
    }

    when (activeOverlay) {
        ActiveOverlay.VOICE_SESSION -> {
            VoiceSessionScreen(
                companionState = companionState,
                activeEmotion = activeEmotion,
                lastExtractedMemory = lastExtractedMemory,
                onSendVoiceTurnWithMetrics = { utterance, langTag, onStreamEvent, onDone, onError ->
                    viewModel.sendUserMessage(
                        userText = utterance,
                        isVoiceMode = true,
                        preferredLanguageTag = langTag,
                        onStreamingChunkEvent = onStreamEvent,
                        onCompleteReply = onDone,
                        onError = onError
                    )
                },
                onCancelActiveGeneration = {
                    viewModel.cancelActiveGeneration()
                },
                onCloseSession = {
                    viewModel.cancelActiveGeneration()
                    activeOverlay = ActiveOverlay.NONE
                }
            )
        }
        ActiveOverlay.TEXT_CONVERSATION -> {
            TextConversationScreen(
                companionState = companionState,
                messages = activeMessages,
                aiTurnState = aiTurnState,
                activeEmotion = activeEmotion,
                lastExtractedMemory = lastExtractedMemory,
                isSearchingWeb = isSearchingWeb,
                lastWebGroundedSources = lastWebGroundedSources,
                onSendMessage = { text ->
                    viewModel.sendUserMessage(
                        userText = text,
                        isVoiceMode = false,
                        preferredLanguageTag = appLanguage.voiceTag
                    )
                },
                onRetryFailedMessage = {
                    viewModel.retryLastFailedMessage(
                        isVoiceMode = false,
                        preferredLanguageTag = appLanguage.voiceTag
                    )
                },
                onSwitchToVoice = {
                    activeOverlay = ActiveOverlay.VOICE_SESSION
                },
                onBack = {
                    activeOverlay = ActiveOverlay.NONE
                }
            )
        }
        ActiveOverlay.NONE -> {
            val homeLabel = tr("Ana Ekran", "Home")
            val characterLabel = tr("Karakter", "Character")

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isExpandedScreen = maxWidth >= 720.dp

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!isExpandedScreen) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == PrimaryTab.HOME,
                                    onClick = { selectedTab = PrimaryTab.HOME },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == PrimaryTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = homeLabel
                                        )
                                    },
                                    label = { Text(homeLabel) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_tab_home")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == PrimaryTab.CHARACTER,
                                    onClick = {
                                        characterSubSection = CharacterSubSection.APPEARANCE
                                        selectedTab = PrimaryTab.CHARACTER
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == PrimaryTab.CHARACTER) Icons.Filled.Face else Icons.Outlined.Face,
                                            contentDescription = characterLabel
                                        )
                                    },
                                    label = { Text(characterLabel) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_tab_character")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isExpandedScreen) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                NavigationRailItem(
                                    selected = selectedTab == PrimaryTab.HOME,
                                    onClick = { selectedTab = PrimaryTab.HOME },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == PrimaryTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = homeLabel
                                        )
                                    },
                                    label = { Text(homeLabel) },
                                    modifier = Modifier.testTag("nav_rail_home")
                                )
                                NavigationRailItem(
                                    selected = selectedTab == PrimaryTab.CHARACTER,
                                    onClick = {
                                        characterSubSection = CharacterSubSection.APPEARANCE
                                        selectedTab = PrimaryTab.CHARACTER
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == PrimaryTab.CHARACTER) Icons.Filled.Face else Icons.Outlined.Face,
                                            contentDescription = characterLabel
                                        )
                                    },
                                    label = { Text(characterLabel) },
                                    modifier = Modifier.testTag("nav_rail_character")
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (selectedTab) {
                                PrimaryTab.HOME -> {
                                    HomeScreen(
                                        companionState = companionState,
                                        conversations = conversations,
                                        memories = memories,
                                        activeEmotion = activeEmotion,
                                        visualState = visualState,
                                        onTriggerReaction = { viewModel.triggerReaction() },
                                        onStartVoiceSession = {
                                            viewModel.openOrCreateConversation(
                                                existingConversationId = null,
                                                mode = "VOICE"
                                            ) {
                                                activeOverlay = ActiveOverlay.VOICE_SESSION
                                            }
                                        },
                                        onStartNewTextChat = {
                                            viewModel.openOrCreateConversation(
                                                existingConversationId = null,
                                                mode = "TEXT"
                                            ) {
                                                activeOverlay = ActiveOverlay.TEXT_CONVERSATION
                                            }
                                        },
                                        onOpenConversation = { conv ->
                                            viewModel.selectConversation(conv.id)
                                            activeOverlay = if (conv.mode == "VOICE") {
                                                ActiveOverlay.VOICE_SESSION
                                            } else {
                                                ActiveOverlay.TEXT_CONVERSATION
                                            }
                                        },
                                        onDeleteConversation = { id ->
                                            viewModel.deleteConversation(id)
                                        },
                                        onOpenSettingsAndPrivacy = {
                                            showSettingsSheet = true
                                        },
                                        onJumpToDiscoveryQuiz = {
                                            characterSubSection = CharacterSubSection.DISCOVERY
                                            selectedTab = PrimaryTab.CHARACTER
                                        }
                                    )
                                }
                                PrimaryTab.CHARACTER -> {
                                    CharacterScreen(
                                        companionState = companionState,
                                        initialSubSection = characterSubSection,
                                        isQuizSynthesizing = isQuizSynthesizing,
                                        pendingProposal = pendingProposal,
                                        onSaveAppearance = { viewModel.updateAppearance(it) },
                                        onSaveClothing = { viewModel.updateClothing(it) },
                                        onSaveAppearanceAndClothing = { app, cloth, msg ->
                                            viewModel.updateAppearanceAndClothing(app, cloth, msg)
                                        },
                                        onSaveVoicePreferences = { voicePrefs ->
                                            viewModel.updateVoicePreferences(voicePrefs)
                                        },
                                        onApplyAiVisualRecommendation = {
                                            viewModel.applyAiVisualRecommendation()
                                        },
                                        onSaveIdentityAndProfile = { identity, ideal, traits ->
                                            viewModel.updateIdentityAndProfile(identity, ideal, traits)
                                        },
                                        onSaveDiscoveryProgress = { dims ->
                                            viewModel.saveDiscoveryProgress(dims)
                                        },
                                        onSynthesizeDiscoveryDimensions = { dims ->
                                            viewModel.synthesizeDiscoveryDimensions(dims)
                                        },
                                        onApproveDiscoveryProposal = { approved ->
                                            viewModel.approveDiscoveryProposal(approved)
                                        },
                                        onDiscardDiscoveryProposal = {
                                            viewModel.discardDiscoveryProposal()
                                        },
                                        onSynthesizeFromQuiz = { a1, a2, a3, a4, onDone ->
                                            viewModel.runDiscoveryQuizSynthesis(a1, a2, a3, a4, onDone)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showSettingsSheet) {
                SettingsAndPrivacySheet(
                    companionState = companionState,
                    memories = memories,
                    usageSnapshot = usageSnapshot,
                    quotaConfig = quotaConfig,
                    onUpdateVoicePreferences = { viewModel.updateVoicePreferences(it) },
                    onUpdatePrivacySettings = { viewModel.updatePrivacySettings(it) },
                    onUpdateQuotaConfig = { viewModel.updateQuotaConfig(it) },
                    onAddManualMemory = { cat, text, imp -> viewModel.addManualMemory(cat, text, imp) },
                    onUpdateMemory = { id, cat, text, imp -> viewModel.updateExistingMemory(id, cat, text, imp) },
                    onDeleteMemory = { viewModel.deleteMemory(it) },
                    onClearAllMemories = { viewModel.clearAllMemories() },
                    onDeleteAllUserData = {
                        viewModel.deleteAllAccountData {
                            showSettingsSheet = false
                            onExitGuestSession()
                            if (!isGuestUser) {
                                signOutUser(credentialManager, scope)
                            }
                        }
                    },
                    onSignOut = {
                        showSettingsSheet = false
                        onExitGuestSession()
                        if (!isGuestUser) {
                            signOutUser(credentialManager, scope)
                        }
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }
        }
    }
}
