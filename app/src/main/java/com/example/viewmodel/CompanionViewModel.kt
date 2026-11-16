package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.CompanionAiService
import com.example.ai.MemoryEngine
import com.example.ai.MemoryResolutionAction
import com.example.ai.PersonalityEngine
import com.example.ai.StructuredPersonalityModel
import com.example.ai.StreamingChunkEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.example.data.model.CharacterAppearance
import com.example.data.model.CharacterIdentity
import com.example.data.model.ClothingAndAccessories
import com.example.data.model.CompanionState
import com.example.data.model.Conversation
import com.example.data.model.DiscoveryDimensions
import com.example.data.model.IdealSelfProfile
import com.example.data.model.LongTermMemory
import com.example.data.model.Message
import com.example.data.model.PersonalityTraits
import com.example.data.model.UserPrivacySettings
import com.example.data.model.VoicePreferences
import com.example.data.repository.CompanionRepository
import com.example.telemetry.ProductionDiagnosticLogger
import com.example.telemetry.RateLimitAllowance
import com.example.telemetry.UsageAndCostEngine
import com.example.telemetry.UsageQuotaConfig
import com.example.telemetry.UsageTelemetrySnapshot
import com.example.ui.components.CharacterVisualState
import com.example.ui.components.PixelAssetCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "CompanionViewModel"

sealed interface AiTurnState {
    data object Idle : AiTurnState
    data object Thinking : AiTurnState
    data class Streaming(val partialReply: String) : AiTurnState
    data class Error(val message: String) : AiTurnState
}

class CompanionViewModel(
    private val repository: CompanionRepository,
    private val aiService: CompanionAiService,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String
) : ViewModel() {

    private val _companionState = MutableStateFlow(CompanionState())
    val companionState: StateFlow<CompanionState> = _companionState.asStateFlow()

    private val _isProfileLoaded = MutableStateFlow(false)
    val isProfileLoaded: StateFlow<Boolean> = _isProfileLoaded.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _aiTurnState = MutableStateFlow<AiTurnState>(AiTurnState.Idle)
    val aiTurnState: StateFlow<AiTurnState> = _aiTurnState.asStateFlow()

    private val _activeEmotion = MutableStateFlow("warm_smile")
    val activeEmotion: StateFlow<String> = _activeEmotion.asStateFlow()

    private val _visualState = MutableStateFlow(CharacterVisualState.IDLE)
    val visualState: StateFlow<CharacterVisualState> = _visualState.asStateFlow()

    private val _lastExtractedMemory = MutableStateFlow<String?>(null)
    val lastExtractedMemory: StateFlow<String?> = _lastExtractedMemory.asStateFlow()

    private val _lastFailedUserMessage = MutableStateFlow<String?>(null)
    val lastFailedUserMessage: StateFlow<String?> = _lastFailedUserMessage.asStateFlow()

    private val _isQuizSynthesizing = MutableStateFlow(false)
    val isQuizSynthesizing: StateFlow<Boolean> = _isQuizSynthesizing.asStateFlow()

    private val _pendingDiscoveryProposal = MutableStateFlow<CompanionState?>(null)
    val pendingDiscoveryProposal: StateFlow<CompanionState?> = _pendingDiscoveryProposal.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<Message>>(emptyList())
    val activeMessages: StateFlow<List<Message>> = _activeMessages.asStateFlow()

    private val _isSearchingWeb = MutableStateFlow(false)
    val isSearchingWeb: StateFlow<Boolean> = _isSearchingWeb.asStateFlow()

    private val _lastWebGroundedSources = MutableStateFlow<String?>(null)
    val lastWebGroundedSources: StateFlow<String?> = _lastWebGroundedSources.asStateFlow()

    @Volatile
    private var cachedStructuredModel: StructuredPersonalityModel? = null

    private val usageEngine = UsageAndCostEngine()
    val quotaConfig: StateFlow<UsageQuotaConfig> = usageEngine.quotaConfig
    val usageSnapshot: StateFlow<UsageTelemetrySnapshot> = usageEngine.snapshot

    private var messagesJob: Job? = null
    private var reactionJob: Job? = null
    private var activeGenerationJob: Job? = null

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing conversations", rootError)
            _errorBanner.value = rootError.localizedMessage ?: "Failed to load conversations."
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val memories: StateFlow<List<LongTermMemory>> = repository.observeMemories(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing memories", rootError)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        initializeUserCompanion()
        observeProfileChanges()
    }

    private fun initializeUserCompanion() {
        viewModelScope.launch {
            val result = repository.getOrCreateCompanionState(
                displayName = initialDisplayName,
                email = initialEmail
            )
            result.onSuccess { state ->
                _companionState.value = state
                cachedStructuredModel = PersonalityEngine.buildStructuredModel(state)
                _activeEmotion.value = state.characterAppearance.expressionDefault
                _isProfileLoaded.value = true
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Could not load companion profile."
                _isProfileLoaded.value = true
            }
        }
    }

    private fun observeProfileChanges() {
        viewModelScope.launch {
            repository.observeCompanionState(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Profile listener error", firestoreError ?: error)
                }
                .collect { updated ->
                    if (updated != null) {
                        _companionState.value = updated
                        cachedStructuredModel = PersonalityEngine.buildStructuredModel(updated)
                        _isProfileLoaded.value = true
                    }
                }
        }
    }

    fun clearBanners() {
        _statusBanner.value = null
        _errorBanner.value = null
        _lastExtractedMemory.value = null
        _lastWebGroundedSources.value = null
    }

    fun setVisualState(state: CharacterVisualState) {
        reactionJob?.cancel()
        _visualState.value = state
    }

    fun triggerReaction(expressionOverride: String? = null) {
        reactionJob?.cancel()
        if (expressionOverride != null) {
            _activeEmotion.value = expressionOverride
        }
        _visualState.value = CharacterVisualState.REACTING
        reactionJob = viewModelScope.launch {
            delay(2200L)
            if (_visualState.value == CharacterVisualState.REACTING) {
                _visualState.value = CharacterVisualState.IDLE
            }
        }
    }

    /**
     * Immediately cancels any in-flight AI streaming response (used when the user interrupts
     * the companion mid-sentence during a live voice session).
     */
    fun cancelActiveGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _aiTurnState.value = AiTurnState.Idle
        _visualState.value = CharacterVisualState.LISTENING
    }

    fun updateAppearance(appearance: CharacterAppearance) {
        val next = _companionState.value.copy(characterAppearance = appearance)
        _companionState.value = next
        _activeEmotion.value = appearance.expressionDefault
        persistState(next, "Pixel appearance saved")
    }

    fun updateClothing(clothing: ClothingAndAccessories) {
        val next = _companionState.value.copy(clothingAndAccessories = clothing)
        _companionState.value = next
        persistState(next, "Outfit & aura saved")
    }

    fun updateAppearanceAndClothing(
        appearance: CharacterAppearance,
        clothing: ClothingAndAccessories,
        bannerMessage: String = "Outfit combination applied"
    ) {
        val next = _companionState.value.copy(
            characterAppearance = appearance,
            clothingAndAccessories = clothing
        )
        _companionState.value = next
        _activeEmotion.value = appearance.expressionDefault
        triggerReaction(appearance.expressionDefault)
        persistState(next, bannerMessage)
    }

    fun applyAiVisualRecommendation() {
        val current = _companionState.value
        val rec = PixelAssetCatalog.recommendVisualsForProfile(
            idealSelf = current.idealSelfProfile,
            traits = current.personalityTraits,
            currentAppearance = current.characterAppearance
        )
        updateAppearanceAndClothing(
            appearance = rec.recommendedAppearance,
            clothing = rec.recommendedClothing,
            bannerMessage = "Applied AI visual recommendation"
        )
    }

    fun updateIdentityAndProfile(
        identity: CharacterIdentity,
        idealSelf: IdealSelfProfile,
        traits: PersonalityTraits
    ) {
        val next = _companionState.value.copy(
            characterIdentity = identity,
            idealSelfProfile = idealSelf,
            personalityTraits = traits,
            userAccount = _companionState.value.userAccount.copy(onboardingCompleted = true)
        )
        _companionState.value = next
        persistState(next, "Ideal-Self profile updated")
    }

    fun skipOnboarding() {
        val current = _companionState.value
        val next = current.copy(
            userAccount = current.userAccount.copy(onboardingCompleted = true)
        )
        _companionState.value = next
        persistState(next, "Welcome to your Midnight Pixel Studio")
    }

    fun completeDirectCreation(
        companionName: String,
        archetypeTitle: String,
        coreAspiration: String,
        lifePillars: List<String>,
        appearance: CharacterAppearance,
        clothing: ClothingAndAccessories
    ) {
        val current = _companionState.value
        val next = current.copy(
            userAccount = current.userAccount.copy(onboardingCompleted = true),
            characterIdentity = current.characterIdentity.copy(
                companionName = companionName.trim().ifBlank { "Nova" }.take(60),
                archetypeTitle = archetypeTitle.trim().ifBlank { "The Calm Architect" }.take(100)
            ),
            idealSelfProfile = current.idealSelfProfile.copy(
                coreAspiration = coreAspiration.trim().ifBlank { current.idealSelfProfile.coreAspiration }.take(500),
                lifePillars = if (lifePillars.isEmpty()) current.idealSelfProfile.lifePillars else lifePillars.take(8),
                quizCompleted = true
            ),
            characterAppearance = appearance,
            clothingAndAccessories = clothing
        )
        _companionState.value = next
        _activeEmotion.value = appearance.expressionDefault
        triggerReaction(appearance.expressionDefault)
        persistState(next, "${next.characterIdentity.companionName} is ready!")
    }

    fun saveDiscoveryProgress(dimensions: DiscoveryDimensions) {
        val current = _companionState.value
        val updatedIdeal = dimensions.toIdealSelfProfile(quizCompleted = current.idealSelfProfile.quizCompleted)
        val next = current.copy(idealSelfProfile = updatedIdeal)
        _companionState.value = next
        persistState(next, "Discovery progress saved (Step ${dimensions.savedStepIndex + 1}/5)")
    }

    fun synthesizeDiscoveryDimensions(
        dimensions: DiscoveryDimensions,
        onProposalReady: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isQuizSynthesizing.value = true
            _visualState.value = CharacterVisualState.THINKING
            _errorBanner.value = null

            val aiResult = aiService.synthesizeIdealSelfFromDimensions(dimensions).getOrNull()
            _isQuizSynthesizing.value = false
            _visualState.value = CharacterVisualState.REACTING

            val current = _companionState.value
            val structuredIdeal = dimensions.toIdealSelfProfile(quizCompleted = true)

            val proposedTraits = if (aiResult != null) {
                PersonalityTraits(
                    warmth = aiResult.warmth,
                    directness = aiResult.directness,
                    optimism = aiResult.optimism,
                    playfulness = aiResult.playfulness,
                    analyticalDepth = aiResult.analyticalDepth,
                    traitKeywords = aiResult.traitKeywords
                )
            } else {
                deriveFallbackTraits(dimensions)
            }

            val proposedIdentity = if (aiResult != null) {
                current.characterIdentity.copy(
                    companionName = aiResult.companionName,
                    archetypeTitle = aiResult.archetypeTitle,
                    backstorySummary = aiResult.backstorySummary,
                    greetingStyle = aiResult.greetingStyle,
                    signatureCatchphrase = aiResult.signatureCatchphrase
                )
            } else {
                current.characterIdentity.copy(
                    companionName = current.characterIdentity.companionName.ifBlank { "Nova" },
                    archetypeTitle = deriveArchetypeTitle(dimensions),
                    backstorySummary = "Forged from your 11 Ideal-Self reflections in the Cozy Midnight Pixel Studio.",
                    greetingStyle = "Hey! Ready to align today's choices with your ideal self?",
                    signatureCatchphrase = "Small intentional steps shape who we become."
                )
            }

            val visualRec = PixelAssetCatalog.recommendVisualsForProfile(
                idealSelf = structuredIdeal,
                traits = proposedTraits,
                currentAppearance = current.characterAppearance
            )

            val proposal = current.copy(
                userAccount = current.userAccount.copy(onboardingCompleted = true),
                idealSelfProfile = structuredIdeal.copy(
                    coreAspiration = aiResult?.coreAspiration?.takeIf { it.isNotBlank() } ?: structuredIdeal.coreAspiration
                ),
                characterIdentity = proposedIdentity,
                personalityTraits = proposedTraits,
                characterAppearance = visualRec.recommendedAppearance,
                clothingAndAccessories = visualRec.recommendedClothing
            )

            _pendingDiscoveryProposal.value = proposal
            onProposalReady()
        }
    }

    fun approveDiscoveryProposal(
        approvedState: CompanionState,
        onComplete: () -> Unit = {}
    ) {
        val finalized = approvedState.copy(
            userAccount = approvedState.userAccount.copy(onboardingCompleted = true),
            idealSelfProfile = approvedState.idealSelfProfile.copy(quizCompleted = true)
        )
        _companionState.value = finalized
        _pendingDiscoveryProposal.value = null
        _activeEmotion.value = finalized.characterAppearance.expressionDefault
        triggerReaction(finalized.characterAppearance.expressionDefault)
        persistState(
            finalized,
            "Approved Ideal Self: ${finalized.characterIdentity.companionName} (${finalized.characterIdentity.archetypeTitle})"
        )
        onComplete()
    }

    fun discardDiscoveryProposal() {
        _pendingDiscoveryProposal.value = null
        _visualState.value = CharacterVisualState.IDLE
    }

    private fun deriveArchetypeTitle(d: DiscoveryDimensions): String {
        val text = "${d.desiredPersonality} ${d.personalValues} ${d.ambitions}".lowercase()
        return when {
            text.contains("creat") || text.contains("art") || text.contains("play") -> "The Radiant Creator"
            text.contains("lead") || text.contains("bold") || text.contains("courag") -> "The Bold Pathfinder"
            text.contains("kind") || text.contains("empath") || text.contains("warm") -> "The Gentle Beacon"
            else -> "The Calm Architect"
        }
    }

    private fun deriveFallbackTraits(d: DiscoveryDimensions): PersonalityTraits {
        val combined = "${d.desiredPersonality} ${d.confidence} ${d.humor} ${d.communicationStyle}".lowercase()
        val warmth = if (combined.contains("warm") || combined.contains("empath") || combined.contains("kind")) 90 else 82
        val directness = if (combined.contains("direct") || combined.contains("bold") || combined.contains("honest")) 86 else 74
        val playfulness = if (combined.contains("witty") || combined.contains("play") || combined.contains("humor")) 88 else 72
        val analytical = if (combined.contains("strateg") || combined.contains("philosoph") || combined.contains("deep")) 88 else 78
        return PersonalityTraits(
            warmth = warmth,
            directness = directness,
            optimism = 85,
            playfulness = playfulness,
            analyticalDepth = analytical,
            traitKeywords = listOf("Grounded", "Intentional", "Self-Assured", "Authentic")
        )
    }

    fun updateVoicePreferences(prefs: VoicePreferences) {
        val next = _companionState.value.copy(voicePreferences = prefs)
        _companionState.value = next
        persistState(next, "Voice preferences updated")
    }

    fun updatePrivacySettings(privacy: UserPrivacySettings) {
        val next = _companionState.value.copy(privacySettings = privacy)
        _companionState.value = next
        persistState(next, "Privacy controls updated")
    }

    private fun persistState(state: CompanionState, successMsg: String) {
        viewModelScope.launch {
            repository.saveCompanionState(state)
                .onSuccess {
                    _statusBanner.value = successMsg
                }
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to save changes."
                }
        }
    }

    fun runDiscoveryQuizSynthesis(
        aspirationAnswer: String,
        challengeResponseAnswer: String,
        communicationStyleAnswer: String,
        dailyHabitAnswer: String,
        onComplete: () -> Unit = {}
    ) {
        val currentDims = DiscoveryDimensions.fromIdealSelfProfile(
            _companionState.value.idealSelfProfile,
            _companionState.value.personalityTraits
        ).copy(
            desiredPersonality = aspirationAnswer,
            emotionalResponses = challengeResponseAnswer,
            communicationStyle = communicationStyleAnswer,
            ambitions = dailyHabitAnswer
        )
        synthesizeDiscoveryDimensions(currentDims) {
            _pendingDiscoveryProposal.value?.let { proposal ->
                approveDiscoveryProposal(proposal, onComplete)
            }
        }
    }

    // --- CONVERSATION & MESSAGE ORCHESTRATION ---

    fun openOrCreateConversation(
        existingConversationId: String? = null,
        mode: String = "TEXT",
        onReady: (String) -> Unit = {}
    ) {
        if (!existingConversationId.isNullOrBlank()) {
            selectConversation(existingConversationId)
            onReady(existingConversationId)
            return
        }
        viewModelScope.launch {
            val companionName = _companionState.value.characterIdentity.companionName
            val defaultTitle = if (mode == "VOICE") "Voice Session with $companionName" else "Reflection with $companionName"
            val greeting = _companionState.value.characterIdentity.greetingStyle
            val createRes = repository.createConversation(
                title = defaultTitle,
                mode = mode,
                initialPreview = greeting
            )
            createRes.onSuccess { convId ->
                selectConversation(convId)
                if (_companionState.value.privacySettings.saveConversationHistory && greeting.isNotBlank()) {
                    repository.addMessage(
                        conversationId = convId,
                        sender = "COMPANION",
                        content = greeting,
                        emotionTag = _companionState.value.characterAppearance.expressionDefault
                    )
                    repository.updateConversationSummary(
                        conversationId = convId,
                        lastMessagePreview = greeting,
                        messageCount = 1L
                    )
                } else if (!_companionState.value.privacySettings.saveConversationHistory) {
                    _activeMessages.value = listOf(
                        Message(
                            id = "ephemeral_greeting",
                            userId = currentUserId,
                            conversationId = convId,
                            sender = "COMPANION",
                            content = greeting,
                            emotionTag = _companionState.value.characterAppearance.expressionDefault
                        )
                    )
                }
                onReady(convId)
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Could not start conversation."
            }
        }
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.observeMessages(conversationId, currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Failed to observe messages", firestoreError ?: error)
                }
                .collect { list ->
                    _activeMessages.value = list
                }
        }
    }

    fun sendUserMessage(
        userText: String,
        isVoiceMode: Boolean,
        preferredLanguageTag: String = "tr-TR",
        onStreamingChunk: (String) -> Unit = {},
        onStreamingChunkEvent: (StreamingChunkEvent) -> Unit = {},
        onCompleteReply: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        isRetry: Boolean = false
    ) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        when (val allowance = usageEngine.checkAllowance(isVoiceMode = isVoiceMode)) {
            is RateLimitAllowance.DeniedCooldown -> {
                val msg = "Please wait a moment (${allowance.waitMs}ms) before sending another message."
                _errorBanner.value = msg
                onError(msg)
                return
            }
            is RateLimitAllowance.DeniedDailyMessageQuota -> {
                val msg = "Daily AI message limit (${allowance.limit}) reached. You can adjust limits in Settings."
                _aiTurnState.value = AiTurnState.Error(msg)
                _errorBanner.value = msg
                onError(msg)
                return
            }
            is RateLimitAllowance.DeniedDailyVoiceQuota -> {
                val msg = "Daily voice limit (${allowance.limitMinutes} min) reached. You can adjust limits in Settings."
                _aiTurnState.value = AiTurnState.Error(msg)
                _errorBanner.value = msg
                onError(msg)
                return
            }
            RateLimitAllowance.Allowed -> {
                usageEngine.recordTurnStarted()
            }
        }

        activeGenerationJob?.cancel()
        val state = _companionState.value
        val saveHistory = state.privacySettings.saveConversationHistory
        val historySnapshot = _activeMessages.value
        val alreadyRecordedLast = isRetry && historySnapshot.lastOrNull()?.let {
            it.sender == "USER" && it.content == trimmed
        } == true

        // Optimistic UI update on frame 0 so the user sees their message and Thinking indicator immediately
        if (!alreadyRecordedLast) {
            val optimisticUserMsg = Message(
                id = "optimistic_user_${System.currentTimeMillis()}",
                userId = currentUserId,
                conversationId = _activeConversationId.value.orEmpty(),
                sender = "USER",
                content = trimmed,
                emotionTag = "curious"
            )
            _activeMessages.value = historySnapshot + optimisticUserMsg
        }

        _lastFailedUserMessage.value = null
        _lastWebGroundedSources.value = null
        _aiTurnState.value = AiTurnState.Thinking
        _visualState.value = CharacterVisualState.THINKING

        activeGenerationJob = viewModelScope.launch {
            // Persist conversation & user message concurrently in background without blocking AI stream startup
            val convIdDeferred = async {
                var cid = _activeConversationId.value
                if (cid.isNullOrBlank()) {
                    val created = repository.createConversation(
                        title = trimmed.take(48),
                        mode = if (isVoiceMode) "VOICE" else "TEXT",
                        initialPreview = trimmed
                    )
                    cid = created.getOrNull()
                    if (cid != null) {
                        selectConversation(cid)
                    }
                }
                if (!alreadyRecordedLast && !cid.isNullOrBlank() && saveHistory) {
                    repository.addMessage(
                        conversationId = cid,
                        sender = "USER",
                        content = trimmed,
                        emotionTag = "curious"
                    )
                }
                cid
            }

            var finalReply = ""
            var webGroundedSources = ""
            try {
                val modelSnapshot = cachedStructuredModel ?: PersonalityEngine.buildStructuredModel(state).also {
                    cachedStructuredModel = it
                }

                aiService.streamCompanionReplyWithMetrics(
                    state = state,
                    memories = memories.value,
                    recentMessages = historySnapshot,
                    userMessage = trimmed,
                    isVoiceMode = isVoiceMode,
                    preferredLanguageTag = preferredLanguageTag,
                    cachedStructuredModel = modelSnapshot,
                    onWebLookupStateChanged = { searching ->
                        _isSearchingWeb.value = searching
                    }
                ).collect { chunkEvent ->
                    finalReply = chunkEvent.accumulatedText
                    if (chunkEvent.isWebGrounded && chunkEvent.webSourcesSummary.isNotBlank()) {
                        webGroundedSources = chunkEvent.webSourcesSummary
                        _lastWebGroundedSources.value = chunkEvent.webSourcesSummary
                    }
                    _aiTurnState.value = AiTurnState.Streaming(chunkEvent.accumulatedText)
                    _visualState.value = CharacterVisualState.SPEAKING
                    onStreamingChunk(chunkEvent.accumulatedText)
                    onStreamingChunkEvent(chunkEvent)
                }

                _isSearchingWeb.value = false
                _aiTurnState.value = AiTurnState.Idle
                _visualState.value = CharacterVisualState.REACTING

                if (finalReply.isNotBlank()) {
                    // Deliver final reply to UI/Voice immediately before awaiting background DB summary writes
                    onCompleteReply(finalReply)

                    if (!saveHistory) {
                        _activeMessages.value = _activeMessages.value + Message(
                            id = "local_comp_${System.currentTimeMillis()}",
                            userId = currentUserId,
                            conversationId = _activeConversationId.value.orEmpty(),
                            sender = "COMPANION",
                            content = finalReply,
                            emotionTag = _activeEmotion.value
                        )
                    }

                    // Persist companion message & extract memory asynchronously off the critical UI path
                    launch {
                        val resolvedConvId = convIdDeferred.await()
                        if (!resolvedConvId.isNullOrBlank() && saveHistory) {
                            val nextCount = (_activeMessages.value.size + 1).toLong()
                            repository.addMessage(
                                conversationId = resolvedConvId,
                                sender = "COMPANION",
                                content = finalReply,
                                emotionTag = _activeEmotion.value
                            )
                            val updatedTitle = if (historySnapshot.size <= 1) trimmed.take(50) else null
                            repository.updateConversationSummary(
                                conversationId = resolvedConvId,
                                title = updatedTitle,
                                lastMessagePreview = finalReply.take(200),
                                messageCount = nextCount
                            )
                        }

                        val willExtractMemory = state.privacySettings.autoExtractMemories && MemoryEngine.isSubstantiveTurn(trimmed)
                        usageEngine.recordCompletedTurn(
                            userText = trimmed,
                            companionReply = finalReply,
                            isVoiceMode = isVoiceMode,
                            memoryExtracted = willExtractMemory
                        )
                        if (willExtractMemory) {
                            extractAndStoreMemoryAsync(trimmed, finalReply, resolvedConvId.orEmpty())
                        }
                    }
                }
            } catch (e: CancellationException) {
                _isSearchingWeb.value = false
                throw e
            } catch (e: Exception) {
                _isSearchingWeb.value = false
                Log.e(TAG, "Companion reply generation failed", e)
                ProductionDiagnosticLogger.recordError("sendUserMessage(isVoice=$isVoiceMode)", e)
                val msg = e.localizedMessage ?: "Companion could not respond right now. Tap Retry."
                _lastFailedUserMessage.value = trimmed
                _aiTurnState.value = AiTurnState.Error(msg)
                _visualState.value = CharacterVisualState.IDLE
                _errorBanner.value = msg
                onError(msg)
            }
        }
    }

    fun retryLastFailedMessage(
        isVoiceMode: Boolean = false,
        preferredLanguageTag: String = "tr-TR",
        onStreamingChunkEvent: (StreamingChunkEvent) -> Unit = {},
        onCompleteReply: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val failedText = _lastFailedUserMessage.value
            ?: _activeMessages.value.lastOrNull { it.sender == "USER" }?.content
            ?: return
        sendUserMessage(
            userText = failedText,
            isVoiceMode = isVoiceMode,
            preferredLanguageTag = preferredLanguageTag,
            onStreamingChunkEvent = onStreamingChunkEvent,
            onCompleteReply = onCompleteReply,
            onError = onError,
            isRetry = true
        )
    }

    private fun extractAndStoreMemoryAsync(
        userMessage: String,
        companionReply: String,
        conversationId: String
    ) {
        viewModelScope.launch {
            val extraction = aiService.extractMemoryAndEmotion(userMessage, companionReply).getOrNull()
            if (extraction != null) {
                _activeEmotion.value = extraction.emotionTag
                triggerReaction(extraction.emotionTag)
                if (extraction.shouldSaveMemory && extraction.summary.isNotBlank()) {
                    val resolution = MemoryEngine.resolveCandidateMemory(
                        shouldSave = extraction.shouldSaveMemory,
                        rawSemanticType = extraction.semanticType,
                        rawSummary = extraction.summary,
                        importance = extraction.importance,
                        existingMemories = memories.value
                    )
                    when (resolution) {
                        is MemoryResolutionAction.CreateNew -> {
                            repository.addMemory(
                                category = resolution.firestoreCategory,
                                summary = resolution.formattedSummary,
                                importance = resolution.importance,
                                sourceConversationId = conversationId
                            ).onSuccess {
                                _lastExtractedMemory.value = resolution.formattedSummary
                            }
                        }
                        is MemoryResolutionAction.UpdateExisting -> {
                            repository.updateMemory(
                                memoryId = resolution.existingMemoryId,
                                category = resolution.firestoreCategory,
                                summary = resolution.updatedSummary,
                                importance = resolution.updatedImportance
                            ).onSuccess {
                                _lastExtractedMemory.value = "Updated: ${resolution.updatedSummary}"
                            }
                        }
                        MemoryResolutionAction.IgnoreTrivialOrDuplicate -> {
                            Log.d(TAG, "Skipped trivial or duplicate memory candidate")
                        }
                    }
                }
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
                _activeMessages.value = emptyList()
            }
        }
    }

    fun addManualMemory(category: String, summary: String, importance: Int) {
        if (summary.isBlank()) return
        viewModelScope.launch {
            repository.addMemory(
                category = category,
                summary = summary.trim(),
                importance = importance
            ).onSuccess {
                _statusBanner.value = "Memory added to ${_companionState.value.characterIdentity.companionName}'s bank"
            }
        }
    }

    fun updateExistingMemory(memoryId: String, category: String, summary: String, importance: Int) {
        if (memoryId.isBlank() || summary.isBlank()) return
        viewModelScope.launch {
            repository.updateMemory(
                memoryId = memoryId,
                category = category,
                summary = summary.trim(),
                importance = importance
            ).onSuccess {
                _statusBanner.value = "Memory updated"
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Failed to update memory."
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
        }
    }

    fun updateQuotaConfig(newConfig: UsageQuotaConfig) {
        usageEngine.updateQuotaConfig(newConfig)
        _statusBanner.value = "Usage limits updated (${newConfig.dailyAiMessageLimit} msgs / ${newConfig.dailyVoiceMinutesLimit} voice min)"
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.deleteAllMemories()
                .onSuccess { count ->
                    _statusBanner.value = "Cleared $count memories from cloud bank"
                }
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Could not clear memories."
                }
        }
    }

    fun deleteAllAccountData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            activeGenerationJob?.cancel()
            messagesJob?.cancel()
            repository.deleteAllUserData()
                .onSuccess {
                    _activeConversationId.value = null
                    _activeMessages.value = emptyList()
                    usageEngine.resetSessionCounters()
                    _statusBanner.value = "All cloud profile, conversation, and memory data permanently deleted."
                    onComplete()
                }
                .onFailure { err ->
                    ProductionDiagnosticLogger.recordError("deleteAllAccountData", err)
                    _errorBanner.value = err.localizedMessage ?: "Failed to delete cloud data."
                }
        }
    }
}
