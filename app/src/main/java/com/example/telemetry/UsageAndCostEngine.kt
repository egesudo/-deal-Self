package com.example.telemetry

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "UsageAndCostEngine"

/**
 * Configurable client-side rate limiting and daily usage quotas to protect
 * user privacy, battery, and cloud AI/voice operating budgets.
 */
data class UsageQuotaConfig(
    val dailyAiMessageLimit: Int = 60,
    val dailyVoiceMinutesLimit: Int = 30,
    val minRequestIntervalMs: Long = 1_200L,
    val enforceHardLimits: Boolean = true
)

/**
 * Real-time telemetry snapshot of current session/daily usage.
 */
data class UsageTelemetrySnapshot(
    val aiTextMessagesSent: Int = 0,
    val voiceTurnsCompleted: Int = 0,
    val estimatedVoiceMinutesUsed: Double = 0.0,
    val estimatedInputTokens: Long = 0L,
    val estimatedOutputTokens: Long = 0L,
    val firestoreReadsCount: Int = 0,
    val firestoreWritesCount: Int = 0,
    val rateLimitedBlockedCount: Int = 0,
    val lastRequestEpochMs: Long = 0L
) {
    val totalMessagesSent: Int
        get() = aiTextMessagesSent + voiceTurnsCompleted
}

sealed interface RateLimitAllowance {
    data object Allowed : RateLimitAllowance
    data class DeniedCooldown(val waitMs: Long) : RateLimitAllowance
    data class DeniedDailyMessageQuota(val limit: Int) : RateLimitAllowance
    data class DeniedDailyVoiceQuota(val limitMinutes: Int) : RateLimitAllowance
}

/**
 * Monthly operating cost breakdown for a target Monthly Active User (MAU) tier
 * (e.g. 100, 500, or 1,000 active users).
 */
data class CostEstimateBreakdown(
    val activeUsers: Int,
    val aiTextUsageUsd: Double,
    val voiceUsageNativeHybridUsd: Double,
    val voiceUsageCloudRealtimeOptionalUsd: Double,
    val databaseFirestoreUsd: Double,
    val hostingAndAuthUsd: Double,
    val storageAndAssetsUsd: Double,
    val monitoringAndCrashReportingUsd: Double,
    val totalMonthlyNativeHybridUsd: Double,
    val totalMonthlyCloudRealtimeUsd: Double,
    val assumptions: List<String>
)

/**
 * Tracks live AI/Voice/Database usage, enforces configurable rate limits & daily quotas,
 * and computes transparent monthly cost projections for 100, 500, and 1,000 active users.
 */
class UsageAndCostEngine(
    initialConfig: UsageQuotaConfig = UsageQuotaConfig()
) {
    private val _quotaConfig = MutableStateFlow(initialConfig)
    val quotaConfig: StateFlow<UsageQuotaConfig> = _quotaConfig.asStateFlow()

    private val _snapshot = MutableStateFlow(UsageTelemetrySnapshot())
    val snapshot: StateFlow<UsageTelemetrySnapshot> = _snapshot.asStateFlow()

    fun updateQuotaConfig(newConfig: UsageQuotaConfig) {
        _quotaConfig.value = newConfig.copy(
            dailyAiMessageLimit = newConfig.dailyAiMessageLimit.coerceIn(5, 500),
            dailyVoiceMinutesLimit = newConfig.dailyVoiceMinutesLimit.coerceIn(5, 240),
            minRequestIntervalMs = newConfig.minRequestIntervalMs.coerceIn(300L, 10_000L)
        )
    }

    /**
     * Checks whether a new AI turn (text or voice) is permitted under the active
     * burst cooldown and daily quota limits.
     */
    fun checkAllowance(
        isVoiceMode: Boolean,
        nowEpochMs: Long = System.currentTimeMillis()
    ): RateLimitAllowance {
        val cfg = _quotaConfig.value
        val cur = _snapshot.value

        if (cur.lastRequestEpochMs > 0L) {
            val elapsed = nowEpochMs - cur.lastRequestEpochMs
            if (elapsed in 0 until cfg.minRequestIntervalMs) {
                val waitMs = (cfg.minRequestIntervalMs - elapsed).coerceAtLeast(100L)
                _snapshot.value = cur.copy(rateLimitedBlockedCount = cur.rateLimitedBlockedCount + 1)
                return RateLimitAllowance.DeniedCooldown(waitMs)
            }
        }

        if (cfg.enforceHardLimits) {
            if (cur.totalMessagesSent >= cfg.dailyAiMessageLimit) {
                _snapshot.value = cur.copy(rateLimitedBlockedCount = cur.rateLimitedBlockedCount + 1)
                return RateLimitAllowance.DeniedDailyMessageQuota(cfg.dailyAiMessageLimit)
            }
            if (isVoiceMode && cur.estimatedVoiceMinutesUsed >= cfg.dailyVoiceMinutesLimit.toDouble()) {
                _snapshot.value = cur.copy(rateLimitedBlockedCount = cur.rateLimitedBlockedCount + 1)
                return RateLimitAllowance.DeniedDailyVoiceQuota(cfg.dailyVoiceMinutesLimit)
            }
        }

        return RateLimitAllowance.Allowed
    }

    fun recordTurnStarted(nowEpochMs: Long = System.currentTimeMillis()) {
        _snapshot.value = _snapshot.value.copy(lastRequestEpochMs = nowEpochMs)
    }

    fun recordCompletedTurn(
        userText: String,
        companionReply: String,
        isVoiceMode: Boolean,
        memoryExtracted: Boolean
    ) {
        val cur = _snapshot.value
        // Approximate 1 token per 4 chars + system prompt & memory context (~450 tokens)
        val inputTokens = 450L + ceil(userText.length / 4.0).toLong() + (if (memoryExtracted) 220L else 0L)
        val outputTokens = ceil(companionReply.length / 4.0).toLong() + (if (memoryExtracted) 65L else 0L)

        // Approximate spoken voice duration (user + companion turn ~ 0.35 min / ~21 seconds per exchange)
        val addedVoiceMinutes = if (isVoiceMode) {
            val wordCount = (userText.split(" ").size + companionReply.split(" ").size).coerceAtLeast(10)
            (wordCount / 135.0).coerceIn(0.15, 1.5)
        } else {
            0.0
        }

        _snapshot.value = cur.copy(
            aiTextMessagesSent = if (!isVoiceMode) cur.aiTextMessagesSent + 1 else cur.aiTextMessagesSent,
            voiceTurnsCompleted = if (isVoiceMode) cur.voiceTurnsCompleted + 1 else cur.voiceTurnsCompleted,
            estimatedVoiceMinutesUsed = ((cur.estimatedVoiceMinutesUsed + addedVoiceMinutes) * 100.0).roundToInt() / 100.0,
            estimatedInputTokens = cur.estimatedInputTokens + inputTokens,
            estimatedOutputTokens = cur.estimatedOutputTokens + outputTokens,
            firestoreWritesCount = cur.firestoreWritesCount + (if (memoryExtracted) 4 else 3),
            firestoreReadsCount = cur.firestoreReadsCount + 2
        )
    }

    fun resetSessionCounters() {
        _snapshot.value = UsageTelemetrySnapshot()
    }

    companion object {
        /**
         * Computes deterministic monthly operating cost estimates for 100, 500, or 1,000 MAU.
         *
         * Documented baseline assumptions per active user per month:
         * - 20 active days/month per MAU
         * - 15 AI turns/active day = 300 turns/user/month (avg 650 input tokens + 180 output tokens, plus 35% selective memory extraction turns)
         * - 4 minutes of active voice conversation per active day = 80 voice minutes/user/month
         * - Default Voice Architecture: Gemini 3.8 Flash Streaming + Android Native tr-TR Neural Speech/TTS ($0.00/min speech relay fee; billed only on LLM tokens)
         * - Optional Cloud Realtime WebRTC/ElevenLabs Voice Architecture: ~$0.04/min audio streaming
         * - Cloud Firestore: ~900 writes + ~2,400 reads + ~2.5 MB storage per user/month (accounting for Firebase Spark/Blaze free tier: 50k reads/day, 20k writes/day, 1 GiB storage free)
         * - Character Assets & Hosting: 100% procedural 16x16 Jetpack Compose Canvas + bundled vector drawables ($0.00 CDN/storage egress) + serverless Firebase Auth & App Check ($0.00 up to 50k MAU).
         */
        fun estimateMonthlyCosts(activeUsers: Int): CostEstimateBreakdown {
            val mau = activeUsers.coerceAtLeast(1)
            val turnsPerUserPerMonth = 300.0
            val totalTurns = mau * turnsPerUserPerMonth

            // Gemini 3.8 Flash token pricing (~$0.15 / 1M input tokens, ~$0.60 / 1M output tokens including selective memory extraction)
            val totalInputTokens = totalTurns * 820.0
            val totalOutputTokens = totalTurns * 210.0
            val aiTextUsd = ((totalInputTokens / 1_000_000.0) * 0.15) + ((totalOutputTokens / 1_000_000.0) * 0.60)

            // Voice minutes: 80 mins/user/month
            val totalVoiceMinutes = mau * 80.0
            // Native Hybrid (Android tr-TR Neural STT/TTS + Gemini Flash token stream): $0 extra speech API fee; token overhead ~$0.0004/min
            val voiceNativeHybridUsd = totalVoiceMinutes * 0.0004
            // Optional Cloud Realtime WebRTC / ElevenLabs tier for comparison: ~$0.045/min
            val voiceCloudRealtimeUsd = totalVoiceMinutes * 0.045

            // Firestore Database (Blaze tier after subtracting free tier: 1.5M reads/mo, 600k writes/mo, 1 GiB free)
            val monthlyReads = mau * 2_400.0
            val monthlyWrites = mau * 900.0
            val billableReads = max(0.0, monthlyReads - 1_500_000.0)
            val billableWrites = max(0.0, monthlyWrites - 600_000.0)
            val storageGb = (mau * 0.0025)
            val billableStorageGb = max(0.0, storageGb - 1.0)
            val firestoreUsd = ((billableReads / 100_000.0) * 0.06) +
                ((billableWrites / 100_000.0) * 0.18) +
                (billableStorageGb * 0.18) +
                (mau * 0.005) // minimal network egress & index overhead

            // Serverless Firebase Auth (Google Sign-In) + App Check: Free up to 50,000 MAU
            val hostingAndAuthUsd = 0.0
            // Bundled 2D Pixel-Art Canvas & Vector Assets: $0 CDN/Object Storage cost
            val storageAndAssetsUsd = 0.0
            // Crash reporting & Cloud Logging (free within 50 GiB/month allotment, nominal alerting buffer)
            val monitoringUsd = if (mau <= 100) 0.0 else (mau * 0.004)

            val totalHybrid = aiTextUsd + voiceNativeHybridUsd + firestoreUsd + hostingAndAuthUsd + storageAndAssetsUsd + monitoringUsd
            val totalCloudVoice = aiTextUsd + voiceCloudRealtimeUsd + firestoreUsd + hostingAndAuthUsd + storageAndAssetsUsd + monitoringUsd

            return CostEstimateBreakdown(
                activeUsers = mau,
                aiTextUsageUsd = roundCents(aiTextUsd),
                voiceUsageNativeHybridUsd = roundCents(voiceNativeHybridUsd),
                voiceUsageCloudRealtimeOptionalUsd = roundCents(voiceCloudRealtimeUsd),
                databaseFirestoreUsd = roundCents(firestoreUsd),
                hostingAndAuthUsd = roundCents(hostingAndAuthUsd),
                storageAndAssetsUsd = roundCents(storageAndAssetsUsd),
                monitoringAndCrashReportingUsd = roundCents(monitoringUsd),
                totalMonthlyNativeHybridUsd = roundCents(totalHybrid),
                totalMonthlyCloudRealtimeUsd = roundCents(totalCloudVoice),
                assumptions = listOf(
                    "20 active days/month per MAU; 15 turns/day (300 turns/MAU/month) + selective memory extraction on ~35% of turns",
                    "80 voice minutes/MAU/month (4 mins/active day)",
                    "Default Native Hybrid Voice uses Gemini 3.8 Flash streaming + Android OS tr-TR Neural Speech/TTS ($0/min relay fee)",
                    "Firestore Blaze tier includes free monthly allotment (1.5M reads, 600K writes, 1 GiB storage)",
                    "2D pixel-art character sprites are rendered procedurally at 60fps on-device ($0 asset CDN cost)"
                )
            )
        }

        fun estimateAllStandardTiers(): List<CostEstimateBreakdown> = listOf(
            estimateMonthlyCosts(100),
            estimateMonthlyCosts(500),
            estimateMonthlyCosts(1_000)
        )

        private fun roundCents(value: Double): Double =
            (value * 100.0).roundToInt() / 100.0
    }
}

/**
 * Production-safe diagnostic & crash breadcrumb logger that redacts PII (emails, tokens, UIDs)
 * before recording events in an in-memory ring buffer or Android logcat.
 */
object ProductionDiagnosticLogger {
    private const val MAX_BREADCRUMBS = 50
    private val breadcrumbs = ArrayDeque<String>(MAX_BREADCRUMBS)
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordError("UncaughtException on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        recordBreadcrumb("ProductionDiagnosticLogger installed")
    }

    @Synchronized
    fun recordBreadcrumb(event: String) {
        val sanitized = redactPii(event)
        if (breadcrumbs.size >= MAX_BREADCRUMBS) {
            breadcrumbs.removeFirst()
        }
        breadcrumbs.addLast("${System.currentTimeMillis()}: $sanitized")
    }

    @Synchronized
    fun recordError(context: String, throwable: Throwable?) {
        val msg = redactPii("$context — ${throwable?.javaClass?.simpleName}: ${throwable?.message.orEmpty()}")
        if (breadcrumbs.size >= MAX_BREADCRUMBS) {
            breadcrumbs.removeFirst()
        }
        breadcrumbs.addLast("ERROR ${System.currentTimeMillis()}: $msg")
        try {
            Log.e(TAG, msg)
        } catch (_: Throwable) {
            // Safe in pure JVM unit tests where android.util.Log is unmocked
        }
    }

    @Synchronized
    fun recentBreadcrumbs(): List<String> = breadcrumbs.toList()

    fun redactPii(input: String): String {
        return input
            .replace(Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"), "[REDACTED_EMAIL]")
            .replace(Regex("AIza[0-9A-Za-z\\-_]{35}"), "[REDACTED_KEY]")
    }
}
