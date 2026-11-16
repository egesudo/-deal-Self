package com.example.ai

import android.os.SystemClock
import android.util.Log
import com.example.data.model.CompanionState
import com.example.data.model.DiscoveryDimensions
import com.example.data.model.LongTermMemory
import com.example.data.model.Message
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "CompanionAiService"
private const val DEFAULT_MODEL = "gemini-3.8-flash"
private const val FAST_CONVERSATION_MODEL = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 45_000L

data class QuizSynthesisResult(
    val companionName: String,
    val archetypeTitle: String,
    val coreAspiration: String,
    val backstorySummary: String,
    val greetingStyle: String,
    val signatureCatchphrase: String,
    val warmth: Int,
    val directness: Int,
    val optimism: Int,
    val playfulness: Int,
    val analyticalDepth: Int,
    val traitKeywords: List<String>
)

data class MemoryExtractionResult(
    val shouldSaveMemory: Boolean,
    val semanticType: String,
    val category: String,
    val summary: String,
    val importance: Int,
    val emotionTag: String
)

data class StreamingChunkEvent(
    val accumulatedText: String,
    val deltaText: String,
    val firstTokenLatencyMs: Long,
    val isWebGrounded: Boolean = false,
    val webSourcesSummary: String = ""
)

class CompanionAiService {

    // Ultra-fast model for real-time voice calls (concise spoken turns)
    private val fastVoiceChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_CONVERSATION_MODEL,
            generationConfig = generationConfig {
                temperature = 0.8f
                maxOutputTokens = 180
            }
        )
    }

    // Ultra-fast model for responsive text chat turns
    private val fastTextChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_CONVERSATION_MODEL,
            generationConfig = generationConfig {
                temperature = 0.8f
                maxOutputTokens = 420
            }
        )
    }

    // Frontier model for web-grounded factual & current-events synthesis
    private val groundedWebChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                temperature = 0.55f
                maxOutputTokens = 480
            }
        )
    }

    private val quizSynthesisModel by lazy {
        val quizSchema = Schema.obj(
            properties = mapOf(
                "companionName" to Schema.string("Short memorable name for the 2D pixel companion"),
                "archetypeTitle" to Schema.string("Title of the ideal-self archetype, e.g. The Calm Architect"),
                "coreAspiration" to Schema.string("1-2 sentence synthesis of who the user aspires to become"),
                "backstorySummary" to Schema.string("Short origin story of this pixel spirit companion"),
                "greetingStyle" to Schema.string("Warm in-character opening greeting"),
                "signatureCatchphrase" to Schema.string("Inspiring motto under 120 characters"),
                "warmth" to Schema.integer("Warmth score 0 to 100"),
                "directness" to Schema.integer("Directness score 0 to 100"),
                "optimism" to Schema.integer("Optimism score 0 to 100"),
                "playfulness" to Schema.integer("Playfulness score 0 to 100"),
                "analyticalDepth" to Schema.integer("Analytical depth score 0 to 100"),
                "traitKeywords" to Schema.array(
                    items = Schema.string(),
                    description = "4 concise personality trait keywords"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizSchema
            }
        )
    }

    private val memoryExtractionModel by lazy {
        val memorySchema = Schema.obj(
            properties = mapOf(
                "shouldSaveMemory" to Schema.boolean(
                    "True ONLY if the user explicitly stated a concrete personal interest, experience, goal, decision, preference, commitment, or change in opinion. False for greetings, small talk, or general questions."
                ),
                "semanticType" to Schema.enumeration(
                    values = listOf(
                        "INTEREST", "EXPERIENCE", "GOAL", "DISCUSSION",
                        "DECISION", "PREFERENCE", "COMMITMENT", "OPINION_SHIFT",
                        "VALUE", "MILESTONE", "INSIGHT"
                    ),
                    description = "Specific semantic classification of the memory"
                ),
                "summary" to Schema.string(
                    "Concise 1-sentence factual summary of what the user shared (in the same language the user spoke). Never invent or extrapolate facts."
                ),
                "importance" to Schema.integer("Importance rating from 1 to 5"),
                "emotionTag" to Schema.enumeration(
                    values = listOf("warm_smile", "determined", "curious", "serene", "joyful", "wink"),
                    description = "The companion's facial expression matching its reply"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = FAST_CONVERSATION_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = memorySchema
            }
        )
    }

    fun buildSystemContext(
        state: CompanionState,
        memories: List<LongTermMemory>,
        isVoiceMode: Boolean,
        currentUserTurn: String = "",
        recentConversationText: String = "",
        preferredLanguageTag: String = "tr-TR",
        cachedStructuredModel: StructuredPersonalityModel? = null,
        webGroundingBlock: String = ""
    ): String {
        val structuredModel = cachedStructuredModel ?: PersonalityEngine.buildStructuredModel(state)
        val respondInTurkish = PersonalityEngine.shouldRespondInTurkish(currentUserTurn, preferredLanguageTag)
        val personalityDirective = PersonalityEngine.buildPersonalityDirective(
            state = state,
            structuredModel = structuredModel,
            isVoiceMode = isVoiceMode,
            respondInTurkish = respondInTurkish,
            currentUserTurn = currentUserTurn
        )

        val limit = minOf(state.privacySettings.contextWindowMemoryLimit.coerceIn(1, 15), 5)
        val rankedMemories = MemoryEngine.retrieveRankedMemories(
            allMemories = memories,
            currentUserTurn = currentUserTurn,
            recentConversationText = recentConversationText,
            limit = limit
        )

        val memorySection = if (rankedMemories.isEmpty()) {
            "No prior memories recorded yet. Do NOT fabricate past conversations."
        } else {
            rankedMemories.joinToString("\n") {
                "- [${MemoryEngine.extractSemanticBadge(it)}] ${MemoryEngine.stripTagPrefix(it.summary)}"
            }
        }

        val webSection = if (webGroundingBlock.isNotBlank()) "\n\n$webGroundingBlock" else ""

        return """
            $personalityDirective

            VERIFIED MEMORIES (NEVER fabricate memories outside this list):
            $memorySection$webSection
        """.trimIndent()
    }

    fun streamCompanionReplyWithMetrics(
        state: CompanionState,
        memories: List<LongTermMemory>,
        recentMessages: List<Message>,
        userMessage: String,
        isVoiceMode: Boolean,
        preferredLanguageTag: String = "tr-TR",
        cachedStructuredModel: StructuredPersonalityModel? = null,
        onWebLookupStateChanged: (Boolean) -> Unit = {}
    ): Flow<StreamingChunkEvent> = flow {
        val respondInTurkish = PersonalityEngine.shouldRespondInTurkish(userMessage, preferredLanguageTag)
        val needsWebLookup = PersonalityEngine.requiresWebLookup(userMessage)

        var webSnapshot: WebGroundingSnapshot? = null
        if (needsWebLookup) {
            onWebLookupStateChanged(true)
            webSnapshot = try {
                WebSearchGroundingClient.fetchLiveWebContext(
                    rawQuery = userMessage,
                    preferTurkish = respondInTurkish
                )
            } finally {
                onWebLookupStateChanged(false)
            }
        }

        val recentSlice = recentMessages.takeLast(6)
        val recentText = recentSlice.joinToString(" ") { it.content }
        val systemPrompt = buildSystemContext(
            state = state,
            memories = memories,
            isVoiceMode = isVoiceMode,
            currentUserTurn = userMessage,
            recentConversationText = recentText,
            preferredLanguageTag = preferredLanguageTag,
            cachedStructuredModel = cachedStructuredModel,
            webGroundingBlock = webSnapshot?.formatForPrompt().orEmpty()
        )

        val historyContext = if (recentSlice.isEmpty()) {
            ""
        } else {
            "\nRECENT TURNS:\n" + recentSlice.joinToString("\n") { msg ->
                val roleLabel = if (msg.sender == "COMPANION") state.characterIdentity.companionName else state.userAccount.displayName
                "$roleLabel: ${msg.content}"
            }
        }

        val fullPrompt = """
            $systemPrompt
            $historyContext

            ${state.userAccount.displayName}: $userMessage
            ${state.characterIdentity.companionName}:
        """.trimIndent()

        val startMs = SystemClock.elapsedRealtime()
        var firstTokenMs = -1L
        val accumulated = StringBuilder()

        val activeModel = when {
            needsWebLookup -> groundedWebChatModel
            isVoiceMode -> fastVoiceChatModel
            else -> fastTextChatModel
        }

        val isWebGrounded = webSnapshot != null
        val sourcesSummary = webSnapshot?.sourcesSummary.orEmpty()

        withTimeout(AI_TIMEOUT_MS) {
            activeModel.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    if (firstTokenMs < 0L) {
                        firstTokenMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                    }
                    accumulated.append(text)
                    emit(
                        StreamingChunkEvent(
                            accumulatedText = accumulated.toString(),
                            deltaText = text,
                            firstTokenLatencyMs = firstTokenMs,
                            isWebGrounded = isWebGrounded,
                            webSourcesSummary = sourcesSummary
                        )
                    )
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Model returned an empty response.")
        }
    }

    fun streamCompanionReply(
        state: CompanionState,
        memories: List<LongTermMemory>,
        recentMessages: List<Message>,
        userMessage: String,
        isVoiceMode: Boolean
    ): Flow<String> = flow {
        streamCompanionReplyWithMetrics(
            state = state,
            memories = memories,
            recentMessages = recentMessages,
            userMessage = userMessage,
            isVoiceMode = isVoiceMode
        ).collect { event ->
            emit(event.accumulatedText)
        }
    }

    suspend fun synthesizeIdealSelfFromDimensions(
        dimensions: DiscoveryDimensions
    ): Result<QuizSynthesisResult> = runCatching {
        val prompt = """
            Based on the user's 11-dimension Ideal Self Discovery responses below, design their personalized 2D pixel-art Ideal Self Companion persona:
            1. Desired Personality: ${dimensions.desiredPersonality}
            2. Personal Values: ${dimensions.personalValues}
            3. Confidence & Self-Trust: ${dimensions.confidence}
            4. Social Behavior: ${dimensions.socialBehavior}
            5. Independence & Autonomy: ${dimensions.independence}
            6. Humor & Playfulness: ${dimensions.humor}
            7. Emotional Responses: ${dimensions.emotionalResponses}
            8. Worldview & Philosophy: ${dimensions.worldview}
            9. Ambitions & Long-Term Vision: ${dimensions.ambitions}
            10. Relationships & Connection: ${dimensions.relationships}
            11. Desired Communication Style: ${dimensions.communicationStyle}
        """.trimIndent()

        parseQuizSynthesisPrompt(prompt, fallbackAspiration = dimensions.desiredPersonality)
    }

    suspend fun synthesizeIdealSelfFromQuiz(
        aspirationAnswer: String,
        challengeResponseAnswer: String,
        communicationStyleAnswer: String,
        dailyHabitAnswer: String
    ): Result<QuizSynthesisResult> = runCatching {
        val prompt = """
            Based on the user's Ideal-Self Discovery Quiz responses below, design their personalized 2D pixel-art Ideal Self Companion persona:
            1. Who they aspire to become: $aspirationAnswer
            2. How their ideal self handles stress or setbacks: $challengeResponseAnswer
            3. Preferred communication & reflection style: $communicationStyleAnswer
            4. Daily signature habit or focus: $dailyHabitAnswer
        """.trimIndent()

        parseQuizSynthesisPrompt(prompt, fallbackAspiration = aspirationAnswer)
    }

    private suspend fun parseQuizSynthesisPrompt(
        prompt: String,
        fallbackAspiration: String
    ): QuizSynthesisResult {
        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                quizSynthesisModel.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("Model returned empty JSON.")
            val json = JSONObject(jsonString)
            val keywordsArr = json.optJSONArray("traitKeywords")
            val keywords = mutableListOf<String>()
            if (keywordsArr != null) {
                for (i in 0 until keywordsArr.length()) {
                    keywords.add(keywordsArr.getString(i))
                }
            }
            return QuizSynthesisResult(
                companionName = json.optString("companionName", "Nova").take(60),
                archetypeTitle = json.optString("archetypeTitle", "The Calm Architect").take(100),
                coreAspiration = json.optString("coreAspiration", fallbackAspiration).take(500),
                backstorySummary = json.optString("backstorySummary", "Born from your highest intentions in the Midnight Studio.").take(600),
                greetingStyle = json.optString("greetingStyle", "Hazır mısın? Bugün kafandaki fazlalıkları biraz sadeleştirelim.").take(300),
                signatureCatchphrase = json.optString("signatureCatchphrase", "Small intentional pixels build the masterpiece.").take(200),
                warmth = json.optInt("warmth", 85).coerceIn(0, 100),
                directness = json.optInt("directness", 78).coerceIn(0, 100),
                optimism = json.optInt("optimism", 82).coerceIn(0, 100),
                playfulness = json.optInt("playfulness", 74).coerceIn(0, 100),
                analyticalDepth = json.optInt("analyticalDepth", 80).coerceIn(0, 100),
                traitKeywords = if (keywords.isEmpty()) listOf("Grounded", "Candid", "Witty", "Intentional") else keywords.take(8)
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quiz synthesis timed out", e)
            throw IllegalStateException("Ideal-Self synthesis timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quiz synthesis failed", e)
            throw e
        }
    }

    suspend fun extractMemoryAndEmotion(
        userMessage: String,
        companionReply: String
    ): Result<MemoryExtractionResult> = runCatching {
        if (!MemoryEngine.isSubstantiveTurn(userMessage)) {
            return@runCatching MemoryExtractionResult(
                shouldSaveMemory = false,
                semanticType = "INSIGHT",
                category = "INSIGHT",
                summary = "",
                importance = 1,
                emotionTag = "warm_smile"
            )
        }

        val prompt = """
            Analyze this conversation turn between the user and their Ideal Self Companion.
            1. Decide if the user explicitly shared a durable fact worth remembering long-term (Interest, Experience, Goal, Discussion topic, Decision, Preference, Commitment, or Opinion Shift).
            2. Do NOT save casual small talk, greetings, or temporary moods. NEVER fabricate details not explicitly stated by the user.
            3. Pick the companion's facial emotion tag matching its reply.

            User said: "$userMessage"
            Companion replied: "$companionReply"
        """.trimIndent()

        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                memoryExtractionModel.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("Empty memory extraction JSON")
            val json = JSONObject(jsonString)
            val rawSemantic = json.optString("semanticType", "INSIGHT")
            val semanticEnum = SemanticMemoryType.fromRawTag(rawSemantic)
            MemoryExtractionResult(
                shouldSaveMemory = json.optBoolean("shouldSaveMemory", false),
                semanticType = semanticEnum.tag,
                category = semanticEnum.firestoreCategory,
                summary = json.optString("summary", "").take(600),
                importance = json.optInt("importance", 3).coerceIn(1, 5),
                emotionTag = json.optString("emotionTag", "warm_smile")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Memory extraction skipped or failed: ${e.message}")
            throw e
        }
    }
}
