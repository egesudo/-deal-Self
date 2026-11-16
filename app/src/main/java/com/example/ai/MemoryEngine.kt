package com.example.ai

import com.example.data.model.LongTermMemory
import kotlin.math.max

/**
 * Semantic memory types covering all 8 required domains:
 * Interests, Experiences, Goals, Discussions, Decisions, Preferences, Commitments, Opinion Shifts.
 * Each maps cleanly to one of the 5 Firestore-validated schema categories:
 * GOAL, VALUE, PREFERENCE, MILESTONE, INSIGHT.
 */
enum class SemanticMemoryType(
    val tag: String,
    val firestoreCategory: String,
    val displayLabel: String
) {
    INTEREST("INTEREST", "PREFERENCE", "Interest"),
    EXPERIENCE("EXPERIENCE", "MILESTONE", "Experience"),
    GOAL("GOAL", "GOAL", "Goal"),
    DISCUSSION("DISCUSSION", "INSIGHT", "Discussion"),
    DECISION("DECISION", "MILESTONE", "Decision"),
    PREFERENCE("PREFERENCE", "PREFERENCE", "Preference"),
    COMMITMENT("COMMITMENT", "GOAL", "Commitment"),
    OPINION_SHIFT("OPINION_SHIFT", "VALUE", "Opinion Change"),
    VALUE("VALUE", "VALUE", "Core Value"),
    MILESTONE("MILESTONE", "MILESTONE", "Milestone"),
    INSIGHT("INSIGHT", "INSIGHT", "Insight");

    companion object {
        fun fromRawTag(raw: String): SemanticMemoryType {
            val normalized = raw.trim().uppercase()
            return entries.firstOrNull { it.tag == normalized || it.firestoreCategory == normalized }
                ?: INSIGHT
        }
    }
}

sealed interface MemoryResolutionAction {
    data object IgnoreTrivialOrDuplicate : MemoryResolutionAction
    data class CreateNew(
        val firestoreCategory: String,
        val formattedSummary: String,
        val importance: Int
    ) : MemoryResolutionAction
    data class UpdateExisting(
        val existingMemoryId: String,
        val firestoreCategory: String,
        val updatedSummary: String,
        val updatedImportance: Int
    ) : MemoryResolutionAction
}

object MemoryEngine {

    private val trivialUtterances = setOf(
        "hi", "hello", "hey", "ok", "okay", "thanks", "thank you", "cool", "yes", "no", "bye",
        "selam", "merhaba", "naber", "nasılsın", "iyiyim", "tamam", "peki", "teşekkürler", "sağol",
        "evet", "hayır", "görüşürüz", "haha", "lol", "hmm", "aynen"
    )

    private val stopWords = setOf(
        "the", "and", "for", "that", "this", "with", "from", "have", "has", "had", "was", "were",
        "user", "wants", "likes", "shared", "mentioned", "about", "their", "they", "bir", "için",
        "olan", "olarak", "daha", "çok", "gibi", "kadar", "sonra", "önce", "kullanıcı", "etti"
    )

    /**
     * Checks whether a user message is substantive enough to even consider for long-term memory.
     * Prevents saving trivial greetings or one-word acknowledgments as permanent memories.
     */
    fun isSubstantiveTurn(userMessage: String): Boolean {
        val cleaned = userMessage.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .trim()
        if (cleaned.length < 10) return false
        if (cleaned in trivialUtterances) return false
        val wordCount = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        return wordCount >= 3
    }

    /**
     * Formats a summary with its semantic sub-tag when helpful (e.g. "[DECISION] Switched career focus...")
     * while ensuring it stays within the 600-character Firestore rule limit.
     */
    fun formatSummaryWithSemanticTag(semanticType: SemanticMemoryType, rawSummary: String): String {
        val trimmed = rawSummary.trim()
        if (trimmed.isEmpty()) return ""
        val prefix = "[${semanticType.tag}] "
        val alreadyTagged = trimmed.startsWith("[") && trimmed.contains("]")
        val combined = if (alreadyTagged) trimmed else "$prefix$trimmed"
        return combined.take(600)
    }

    /**
     * Decides whether a newly extracted candidate memory should be:
     * - Ignored (if trivial, empty, or an exact/near-duplicate of an existing memory)
     * - Updated in-place (if it represents an OPINION_SHIFT, DECISION update, or refines an existing memory on the same subject)
     * - Created as a new memory document
     */
    fun resolveCandidateMemory(
        shouldSave: Boolean,
        rawSemanticType: String,
        rawSummary: String,
        importance: Int,
        existingMemories: List<LongTermMemory>
    ): MemoryResolutionAction {
        val cleanedSummary = rawSummary.trim()
        if (!shouldSave || cleanedSummary.length < 8) {
            return MemoryResolutionAction.IgnoreTrivialOrDuplicate
        }

        val semanticType = SemanticMemoryType.fromRawTag(rawSemanticType)
        val firestoreCategory = semanticType.firestoreCategory
        val formattedSummary = formatSummaryWithSemanticTag(semanticType, cleanedSummary)
        val coercedImportance = importance.coerceIn(1, 5)

        val candidateTokens = tokenize(cleanedSummary)
        if (candidateTokens.isEmpty()) {
            return MemoryResolutionAction.IgnoreTrivialOrDuplicate
        }

        var bestMatch: LongTermMemory? = null
        var highestSimilarity = 0.0

        for (existing in existingMemories) {
            val existingClean = stripTagPrefix(existing.summary)
            if (existingClean.equals(cleanedSummary, ignoreCase = true)) {
                return MemoryResolutionAction.IgnoreTrivialOrDuplicate
            }
            val existingTokens = tokenize(existingClean)
            val sim = computeTokenSimilarity(candidateTokens, existingTokens)
            if (sim > highestSimilarity) {
                highestSimilarity = sim
                bestMatch = existing
            }
        }

        val matched = bestMatch
        if (matched != null) {
            // Exact or near-identical duplicate -> skip creating a redundant memory
            if (highestSimilarity >= 0.78 && semanticType != SemanticMemoryType.OPINION_SHIFT) {
                return MemoryResolutionAction.IgnoreTrivialOrDuplicate
            }

            // Topic overlap with an opinion change, decision revision, or refined detail -> update existing memory
            val isRevisionType = semanticType == SemanticMemoryType.OPINION_SHIFT ||
                semanticType == SemanticMemoryType.DECISION ||
                semanticType == SemanticMemoryType.COMMITMENT ||
                semanticType == SemanticMemoryType.GOAL
            if (highestSimilarity >= 0.45 || (isRevisionType && highestSimilarity >= 0.34)) {
                val mergedImportance = max(coercedImportance, matched.importance.toInt()).coerceIn(1, 5)
                return MemoryResolutionAction.UpdateExisting(
                    existingMemoryId = matched.id,
                    firestoreCategory = firestoreCategory,
                    updatedSummary = formattedSummary,
                    updatedImportance = mergedImportance
                )
            }
        }

        return MemoryResolutionAction.CreateNew(
            firestoreCategory = firestoreCategory,
            formattedSummary = formattedSummary,
            importance = coercedImportance
        )
    }

    /**
     * Ranks and retrieves the top N most relevant memories for the current user message and conversation context.
     * Combines:
     * 1. Keyword/topic overlap with the current user turn and recent messages
     * 2. Memory importance rating (1..5)
     * 3. Recency boost
     */
    fun retrieveRankedMemories(
        allMemories: List<LongTermMemory>,
        currentUserTurn: String,
        recentConversationText: String = "",
        limit: Int = 15
    ): List<LongTermMemory> {
        if (allMemories.isEmpty()) return emptyList()
        val queryTokens = tokenize("$currentUserTurn $recentConversationText")

        return allMemories
            .mapIndexed { index, memory ->
                val memTokens = tokenize(stripTagPrefix(memory.summary))
                val overlapScore = if (queryTokens.isEmpty() || memTokens.isEmpty()) {
                    0.0
                } else {
                    val intersection = queryTokens.intersect(memTokens).size.toDouble()
                    intersection / max(1, minOf(queryTokens.size, memTokens.size)).toDouble()
                }
                val importanceScore = (memory.importance.coerceIn(1L, 5L).toDouble()) / 5.0
                val recencySeconds = (memory.updatedAt ?: memory.createdAt)?.seconds ?: 0L
                // Slight tie-breaker on recency and list position
                val recencyBonus = if (recencySeconds > 0L) 0.1 else (0.05 / (index + 1))

                val totalScore = (overlapScore * 3.0) + (importanceScore * 1.5) + recencyBonus
                Pair(memory, totalScore)
            }
            .sortedByDescending { it.second }
            .take(limit.coerceIn(1, 50))
            .map { it.first }
    }

    fun stripTagPrefix(summary: String): String {
        val trimmed = summary.trim()
        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf(']')
            if (endBracket in 1..22) {
                return trimmed.substring(endBracket + 1).trim()
            }
        }
        return trimmed
    }

    fun extractSemanticBadge(memory: LongTermMemory): String {
        val trimmed = memory.summary.trim()
        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf(']')
            if (endBracket in 1..22) {
                return trimmed.substring(1, endBracket).trim()
            }
        }
        return memory.category
    }

    internal fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    internal fun computeTokenSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        val minSize = minOf(a.size, b.size).toDouble()
        val jaccard = intersection / union
        val containment = intersection / minSize
        return (jaccard * 0.55) + (containment * 0.45)
    }
}
