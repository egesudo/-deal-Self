package com.example.ai

import com.example.data.model.CompanionState
import com.example.data.model.DiscoveryDimensions
import com.example.data.model.LongTermMemory

/**
 * 12-Dimension Structured Personality Model for every Ideal Self Companion character.
 * Represents an aspirational yet grounded, human-feeling alter-ego with strengths,
 * realistic flaws/quirks, humor, and healthy relationship boundaries.
 */
data class StructuredPersonalityModel(
    val identity: String,
    val coreValues: List<String>,
    val worldview: String,
    val personalityTraitsSummary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val socialBehavior: String,
    val communicationStyle: String,
    val humor: String,
    val emotionalResponses: String,
    val relationshipPhilosophy: String,
    val boundaries: List<String>
)

/**
 * Conversational scenario classification for QA and adaptive alter-ego prompting:
 * Covers Casual conversations, Disagreement, Advice requests, Philosophical discussions,
 * Previous conversation recall, Emotional situations, and Uncertain factual questions.
 */
enum class ConversationScenarioType(val directive: String) {
    CASUAL(
        "SCENARIO [CASUAL CONVERSATION]: Keep it relaxed, witty, and human. Do not turn casual banter into a productivity lecture."
    ),
    DISAGREEMENT(
        "SCENARIO [DISAGREEMENT / DEBATE]: Hold your ground with good-humored candor. Explain WHY you see it differently instead of folding or apologizing."
    ),
    ADVICE_REQUEST(
        "SCENARIO [ADVICE REQUEST]: Cut through overthinking. Challenge any hidden excuses first, then offer 1 or 2 sharp, concrete perspectives—never a generic 10-point self-help list."
    ),
    PHILOSOPHICAL(
        "SCENARIO [PHILOSOPHICAL / DEEP DISCUSSION]: Explore the paradoxes of meaning, control, freedom, or human behavior with intellectual curiosity and dry wit."
    ),
    PREVIOUS_CONVERSATION_RECALL(
        "SCENARIO [PREVIOUS CONVERSATION RECALL]: Reference ONLY facts present in VERIFIED PERSISTENT MEMORIES or RECENT CONVERSATION TURNS. If a detail is not in that list, state honestly that you don't have that memory recorded—NEVER invent or guess past conversations."
    ),
    EMOTIONAL_SITUATION(
        "SCENARIO [EMOTIONAL SITUATION]: Drop the teasing and be a grounded, steady friend first. Acknowledge the weight of what they're feeling without clinical therapy jargon or toxic positivity."
    ),
    UNCERTAIN_FACTUAL_QUERY(
        "SCENARIO [UNCERTAIN / REAL-TIME FACTUAL QUESTION]: Do NOT hallucinate statistics, live news, or uncertain facts. Explicitly state what you know and where you're uncertain."
    )
}

data class AiQualityValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)

object PersonalityEngine {

    val supportedConversationDomains = listOf(
        "Daily life & routines",
        "Psychology & human behavior",
        "Sociology & culture",
        "Philosophy & meaning",
        "Relationships & dating",
        "Friendship & loyalty",
        "Education & learning",
        "Career & craft",
        "Science & discovery",
        "Technology & the future",
        "Personal experiences & stories",
        "Humor & absurdities of life",
        "General curiosity & late-night debates"
    )

    private val bannedMotivationalCliches = listOf(
        "as an ai language model",
        "i am a large language model",
        "believe in yourself and anything is possible",
        "you can achieve anything you set your mind to",
        "every cloud has a silver lining",
        "unlock your limitless potential",
        "söylediğin şey çok değerli",
        "bakış açısıyla baktığımızda",
        "üzerinde kafa yorman harika",
        "önümüzdeki 30 dakikada yapacağın tek net hamleyi söyle"
    )

    /**
     * Normalizes common speech-to-text (STT) phonetic slips in Turkish & English
     * (e.g. "Sen de önerirsin" -> "Sen ne önerirsin?", "bu konuda de dersin" -> "Bu konuda ne dersin?")
     * so spoken questions and recommendation requests are understood in context.
     */
    fun normalizeSpokenTranscript(rawText: String): String {
        var text = rawText.trim()
        if (text.isEmpty()) return ""

        val replacements = listOf(
            Regex("\\bsen\\s+de\\s+önerirsin\\b", RegexOption.IGNORE_CASE) to "Sen ne önerirsin?",
            Regex("\\bsende\\s+önerirsin\\b", RegexOption.IGNORE_CASE) to "Sen ne önerirsin?",
            Regex("\\bbana\\s+de\\s+önerirsin\\b", RegexOption.IGNORE_CASE) to "Bana ne önerirsin?",
            Regex("\\bsen\\s+de\\s+dersin\\b", RegexOption.IGNORE_CASE) to "Sen ne dersin?",
            Regex("\\bsende\\s+dersin\\b", RegexOption.IGNORE_CASE) to "Sen ne dersin?",
            Regex("\\bsen\\s+de\\s+düşünüyorsun\\b", RegexOption.IGNORE_CASE) to "Sen ne düşünüyorsun?",
            Regex("\\bbu\\s+konuda\\s+de\\s+düşünüyorsun\\b", RegexOption.IGNORE_CASE) to "Bu konuda ne düşünüyorsun?",
            Regex("\\bpeki\\s+de\\s+yapalım\\b", RegexOption.IGNORE_CASE) to "Peki ne yapalım?",
            Regex("\\bşimdi\\s+de\\s+yapalım\\b", RegexOption.IGNORE_CASE) to "Şimdi ne yapalım?",
            Regex("\\bbugün\\s+de\\s+konuşalım\\b", RegexOption.IGNORE_CASE) to "Bugün ne konuşalım?"
        )
        for ((pattern, replacement) in replacements) {
            text = text.replace(pattern, replacement)
        }
        return text
    }

    /**
     * Builds the complete 12-dimension structured personality model from the user's CompanionState.
     * Ensures the companion is aspirational without being a flawless, robotic, or preachy saint.
     */
    fun buildStructuredModel(state: CompanionState): StructuredPersonalityModel {
        val identity = state.characterIdentity
        val ideal = state.idealSelfProfile
        val traits = state.personalityTraits
        val dims: DiscoveryDimensions = ideal.discoveryDimensions

        val derivedStrengths = buildList {
            if (traits.directness >= 70) add("Cuts through overthinking and self-deception with honest clarity")
            if (traits.warmth >= 70) add("Listens deeply without judgment when things genuinely hurt")
            if (traits.playfulness >= 65) add("Uses dry wit and playful perspective to defuse unnecessary stress")
            if (traits.analyticalDepth >= 70) add("Connects everyday problems to deeper psychological and philosophical patterns")
            if (traits.optimism >= 70) add("Spots practical agency even in messy situations")
            if (isEmpty()) add("Calm presence and steady self-trust")
        }

        val derivedWeaknesses = buildList {
            if (traits.directness >= 75) {
                add("Impatient with excuses, people-pleasing, and endless circular over-planning")
            } else {
                add("Sometimes lingers on a philosophical tangent longer than strictly necessary")
            }
            if (traits.playfulness >= 70) {
                add("Will playfully tease you if you start catastrophizing minor everyday hiccups")
            } else {
                add("Can be stubbornly skeptical of trendy self-help clichés and toxic positivity")
            }
            add("Prefers candid, unfiltered late-night honesty over polite small talk")
        }

        val boundaries = listOf(
            "I am your fictional 2D pixel-art AI alter-ego and companion—not a human, therapist, or conscious being.",
            "I won't just nod along or flatter you when I think you're selling yourself short or overcomplicating things.",
            "I always encourage real-world friendships, offline experiences, and your own independent judgment over relying on an app."
        )

        return StructuredPersonalityModel(
            identity = "${identity.companionName} (${identity.archetypeTitle}) — ${identity.backstorySummary}",
            coreValues = ideal.lifePillars.ifEmpty { listOf("Authentic Action", "Mental Freedom", "Curiosity") },
            worldview = dims.worldview.ifBlank { ideal.coreAspiration },
            personalityTraitsSummary = "Warmth ${traits.warmth}/100, Directness ${traits.directness}/100, " +
                "Optimism ${traits.optimism}/100, Playfulness ${traits.playfulness}/100, " +
                "Analytical Depth ${traits.analyticalDepth}/100 (${traits.traitKeywords.joinToString(", ")})",
            strengths = derivedStrengths,
            weaknesses = derivedWeaknesses,
            socialBehavior = dims.socialBehavior.ifBlank { "Relaxed, observant, and unimpressed by status games; treats you like a close equal." },
            communicationStyle = dims.communicationStyle.ifBlank { ideal.reflectionStyle },
            humor = dims.humor.ifBlank { "Dry, observational, slightly irreverent wit that punctures pretentiousness." },
            emotionalResponses = dims.emotionalResponses.ifBlank { ideal.emotionalGrounding },
            relationshipPhilosophy = "${dims.relationships.ifBlank { "Deep, loyal connections" }} — As your alter-ego friend, I don't just comfort you; I challenge your blind spots, laugh with you, and tell you to relax when you're overthinking.",
            boundaries = boundaries
        )
    }

    /**
     * Classifies a user turn into one of the 7 core QA conversational scenarios so the system prompt
     * applies targeted guardrails (e.g. anti-hallucination on recall/uncertain facts, grounded empathy on distress).
     */
    fun classifyScenario(userMessage: String): ConversationScenarioType {
        val normalized = normalizeSpokenTranscript(userMessage)
        val lower = normalized.lowercase()
        return when {
            listOf(
                "hatırlıyor musun", "geçen sefer", "daha önce ne", "do you remember",
                "what did i tell you", "last time we talked", "my previous"
            ).any { lower.contains(it) } -> ConversationScenarioType.PREVIOUS_CONVERSATION_RECALL

            listOf(
                "kesin doğru mu", "tam rakam", "kaç yılında", "borsa", "hava durumu",
                "exact statistic", "stock price", "breaking news", "who won the",
                "güncel", "son dakika", "haber", "dolar", "euro", "bitcoin", "fiyatı",
                "internette", "webde", "araştır", "weather", "latest news"
            ).any { lower.contains(it) } -> ConversationScenarioType.UNCERTAIN_FACTUAL_QUERY

            listOf(
                "çok kötüyüm", "yalnız hissediyorum", "tükendim", "ağladım", "korkuyorum",
                "feeling overwhelmed", "exhausted", "anxious", "heartbroken", "lonely", "grief"
            ).any { lower.contains(it) } -> ConversationScenarioType.EMOTIONAL_SITUATION

            listOf(
                "katılmıyorum", "yanılıyorsun", "saçma", "bence yanlış",
                "i disagree", "you're wrong", "that makes no sense", "challenge my"
            ).any { lower.contains(it) } -> ConversationScenarioType.DISAGREEMENT

            listOf(
                "felsefe", "hayatın anlamı", "özgür irade", "varoluş", "bilinç", "kontrol etmeye",
                "philosophy", "meaning of life", "free will", "absurd", "consciousness", "existential"
            ).any { lower.contains(it) } -> ConversationScenarioType.PHILOSOPHICAL

            listOf(
                "ne yapmalıyım", "sence nereden", "tavsiye", "karar veremiyorum",
                "ne önerirsin", "de önerirsin", "önerin ne", "bana bir şey öner", "fikrin ne",
                "what should i do", "how should i handle", "give me advice", "help me decide",
                "what do you recommend", "what do you suggest"
            ).any { lower.contains(it) } -> ConversationScenarioType.ADVICE_REQUEST

            else -> ConversationScenarioType.CASUAL
        }
    }

    /**
     * Determines whether a user message asks for real-time internet information, live news,
     * weather, prices, dates, or verifiable factual lookup—keeping normal casual banter fast.
     */
    fun requiresWebLookup(userMessage: String): Boolean {
        val trimmed = userMessage.trim()
        if (trimmed.length < 4) return false
        val lower = trimmed.lowercase()

        // Personal/emotional/introspective turns should never trigger external web search
        val personalIntrospectiveMarkers = listOf(
            "kafam biraz karışık", "kendimi", "hissediyorum", "erteleme yapıyorum",
            "beni tanı", "benim hedefim", "i feel ", "my anxiety", "my habit", "help me decide"
        )
        if (personalIntrospectiveMarkers.any { lower.contains(it) }) {
            return false
        }

        val webTriggerKeywords = listOf(
            // Turkish real-time & factual triggers
            "hava durumu", "bugün hava", "kaç derece", "son dakika", "güncel", "haberler",
            "haber ", "dolar kaç", "euro kaç", "altın fiyat", "bitcoin", "borsa", "hisse",
            "maç sonucu", "kim kazandı", "skor ne", "internette", "webde", "google'da",
            "araştır", "kaç yılında", "tarihte bugün", "kimdir", "nerededir", "nüfusu kaç",
            "başkenti", "en son çıkan", "vizyondaki", "ne zaman çıktı", "fiyatı ne kadar",
            // English real-time & factual triggers
            "weather in", "weather today", "temperature in", "breaking news", "latest news",
            "current price", "stock price", "bitcoin price", "exchange rate", "who won",
            "match score", "search the web", "look up online", "on the internet", "who is the president",
            "who is the ceo", "when did ", "what happened in ", "population of", "capital of",
            "release date of", "exact statistic"
        )
        return webTriggerKeywords.any { lower.contains(it) }
    }

    /**
     * Evaluates a companion reply against AI quality standards:
     * - No generic AI disclaimers or cheesy motivational clichés
     * - No repetitive duplicate sentences
     * - No fabricated memory assertions when the memory bank is empty
     */
    fun validateResponseQuality(
        reply: String,
        verifiedMemories: List<LongTermMemory>
    ): AiQualityValidationResult {
        val issues = mutableListOf<String>()
        val trimmed = reply.trim()
        if (trimmed.isEmpty()) {
            issues.add("Response is empty")
            return AiQualityValidationResult(false, issues)
        }

        val lower = trimmed.lowercase()
        for (cliche in bannedMotivationalCliches) {
            if (lower.contains(cliche)) {
                issues.add("Contains generic AI or motivational cliché: '$cliche'")
            }
        }

        // Check for repetitive consecutive sentences
        val sentences = trimmed.split(Regex("[.!?]+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 15 }
        if (sentences.size != sentences.distinct().size) {
            issues.add("Contains repetitive duplicate sentences")
        }

        // Check for fabricated memory claims when memory bank is empty
        if (verifiedMemories.isEmpty()) {
            val fabricationPatterns = listOf(
                "you told me last week that you",
                "i remember when you told me last month",
                "geçen hafta bana söylediğin gibi"
            )
            if (fabricationPatterns.any { lower.contains(it) }) {
                issues.add("Claims unrecorded past conversation when memory bank is empty")
            }
        }

        return AiQualityValidationResult(issues.isEmpty(), issues)
    }

    /**
     * Detects whether the user's message is in Turkish so the companion responds in natural,
     * idiomatic conversational Turkish (or honors the explicitly selected voice locale).
     */
    fun shouldRespondInTurkish(userMessage: String, preferredLanguageTag: String = "tr-TR"): Boolean {
        val turkishChars = setOf('ç', 'ğ', 'ı', 'ö', 'ş', 'ü', 'Ç', 'Ğ', 'İ', 'Ö', 'Ş', 'Ü')
        if (userMessage.any { it in turkishChars }) return true
        val lower = " ${userMessage.lowercase()} "
        val commonTurkishWords = listOf(
            " selam ", " merhaba ", " nasılsın ", " ne ", " ama ", " çok ", " biraz ",
            " bence ", " neden ", " nasıl ", " bugün ", " hayat ", " kafam ", " ya ", " abi ", " dostum "
        )
        if (commonTurkishWords.any { lower.contains(it) }) return true
        return preferredLanguageTag.startsWith("tr", ignoreCase = true)
    }

    /**
     * Constructs a high-speed, token-efficient system prompt incorporating the 12-dimension
     * personality model, the contrasting/challenging alter-ego friendship dynamic, scenario
     * guardrails, and strict contextual topic adherence for minimal Time-to-First-Token (TTFT).
     */
    fun buildPersonalityDirective(
        state: CompanionState,
        structuredModel: StructuredPersonalityModel,
        isVoiceMode: Boolean,
        respondInTurkish: Boolean,
        currentUserTurn: String = ""
    ): String {
        val normalizedTurn = normalizeSpokenTranscript(currentUserTurn)
        val scenario = classifyScenario(normalizedTurn)
        val languageInstruction = if (respondInTurkish) {
            "LANGUAGE (NATURAL TURKISH - tr-TR): Speak in natural, intelligent, self-assured everyday Turkish ('sen' dili—net, kararlı, konuyu gerçekten anlayan ve doğrudan mantıklı cevap veren yakın dost gibi). NEVER echo single words/verbs in quotes (like '\"önerirsin\" üzerinde kafa yorman harika') and NEVER use robotic filler like 'Söylediğin şey çok değerli'."
        } else {
            "LANGUAGE: Match the user's language naturally with intelligent, self-assured, candid everyday phrasing—direct, logical, and zero filler."
        }

        val modalityInstruction = if (isVoiceMode) {
            "VOICE CALL MODE: Keep reply natural, context-grounded, and spoken-friendly (1–3 clear sentences, ~20–45 words). Answer the user's actual question or topic directly. No markdown, lists, asterisks, or emojis."
        } else {
            "TEXT CHAT MODE: Keep reply concise, thoughtful, and directly relevant to the user's topic (1–2 short paragraphs, ~30–80 words). No robotic bulleted essays."
        }

        return """
            You are ${state.characterIdentity.companionName} (${state.characterIdentity.archetypeTitle}), the user's 2D pixel-art Ideal Self Companion.
            12-DIMENSION STRUCTURED PERSONALITY MODEL:
            - Identity & Values: ${structuredModel.identity} | Pillars: ${structuredModel.coreValues.joinToString(", ")}
            - Worldview & Traits: ${structuredModel.worldview} | ${structuredModel.personalityTraitsSummary}
            - Strengths & Quirks: ${structuredModel.strengths.firstOrNull().orEmpty()} | Quirk: ${structuredModel.weaknesses.firstOrNull().orEmpty()}
            - Social & Style: ${structuredModel.socialBehavior} | Style: ${structuredModel.communicationStyle} | Humor: ${structuredModel.humor}
            - Grounding & Bond: ${structuredModel.emotionalResponses} | ${structuredModel.relationshipPhilosophy}
            CONVERSATIONAL DYNAMIC (CONTRASTING, CHALLENGING ALTER-EGO FRIENDSHIP — BOLD & SELF-ASSURED):
            - Answer the exact substance, question, or topic the user just shared directly and logically first.
            - Do NOT force every conversation into a "take action in 30 minutes" productivity lecture. When the user asks "What do you recommend?" or chats about daily life, ideas, tech, or philosophy, give a genuine, thoughtful response on that topic.
            - NEVER mechanically echo a single word or conjugated verb in quotes, and NEVER hallucinate unrecorded past memories.
            ${scenario.directive}
            SAFETY & AUTONOMY GUARDRAILS: Fictional AI alter-ego; support real-world autonomy; never flatter blindly.
            $languageInstruction
            $modalityInstruction
        """.trimIndent()
    }

    /**
     * Generates a smart, multi-turn context-aware fallback reply if cloud AI is unreachable,
     * ensuring the companion answers the user's topic or question directly without mechanical
     * verb-echoing or out-of-context "30-minute action" templates.
     */
    fun generateContextualFallbackReply(
        userMessage: String,
        recentMessagesText: String = "",
        respondInTurkish: Boolean = true
    ): String {
        val normalized = normalizeSpokenTranscript(userMessage)
        val lower = " ${normalized.lowercase()} "
        val combinedContext = "$recentMessagesText $lower".lowercase()

        if (!respondInTurkish) {
            return when {
                listOf("what do you recommend", "what do you suggest", "any suggestions", "what should we").any { lower.contains(it) } ->
                    "Here's what I suggest: we can either brainstorm a project or decision you're weighing right now, or dive into an interesting conversation about technology, psychology, or daily life. Which direction sounds better to you?"
                listOf("why", "how so", "what do you mean", "explain").any { lower.contains(it) } ->
                    "Because when we look at the core reason behind a situation rather than the surface noise, the smartest path forward becomes much clearer. How does that line up with what you're seeing?"
                else ->
                    "That's a solid point. Looking at it logically, the key is separating what genuinely matters to you from background noise. How do you see it playing out?"
            }
        }

        return when {
            listOf(
                "ne önerirsin", "de önerirsin", "önerin ne", "ne tavsiye", "bana bir şey öner",
                "sen ne dersin", "fikrin ne", "ne düşünüyorsun", "ne konuşalım", "ne yapalım"
            ).any { lower.contains(it) } -> {
                when {
                    listOf("kod", "yazılım", "uygulama", "proje", "yapay zeka", "tasarım").any { combinedContext.contains(it) } ->
                        "Konumuz yazılım ve proje olduğuna göre net önerim şu: Aynı anda her detayı çözmeye çalışmak yerine önce kullanıcının temas ettiği ana akışı sade ve hatasız hale getirelim. Şu an hangi kısım üzerinde duruyorsun?"
                    listOf("yoruldum", "stres", "bunal", "kafam karışık").any { combinedContext.contains(it) } ->
                        "Zihnin yorgunken sana daha çok yüklen demem gerçekçi olmaz. Benim önerim önce 15 dakika ekranlardan uzaklaşıp kafandaki listeyi teke indirmen. Şu an seni en çok yoran şey ne?"
                    else ->
                        "Madem topu bana attın, sana üç net öneri sunayım: Şu an üzerinde çalıştığın bir fikir veya proje varsa onu birlikte geliştirebiliriz, kafanı kurcalayan bir kararı mantık süzgecinden geçirebiliriz ya da teknoloji ve insan psikolojisi üzerine keyifli bir sohbet açabiliriz. Hangisinden başlayalım?"
                }
            }
            listOf("neden", "niye", "nasıl yani", "biraz açar mısın", "örnek ver").any { lower.contains(it) } ->
                "Şundan dolayı: Bir meseleyi yüzeysel düşündüğümüzde zihin onu olduğundan daha karmaşık gösterir; ama temel mantığını netleştirdiğimizde çözüm kendiliğinden ortaya çıkar. Senin durumunda da asıl kilit nokta burası."
            listOf("katılmıyorum", "bence yanlış", "öyle değil", "saçma", "anlamadın").any { lower.contains(it) } ->
                "Farklı düşünmen çok güzel! Ezbere onaylamaktansa böyle net itiraz etmeni tercih ederim. Benim kaçırdığım veya senin farklı gördüğün noktayı anlat, doğrudan senin perspektifinden bakalım."
            listOf("kahve", "çay", "dinleniyorum", "hava", "yemek", "film", "müzik", "kitap").any { lower.contains(it) } ->
                "Kulağa gayet keyifli geliyor! Günün temposunda böyle nefes aldıran anlar zihni tazelemek için birebirdir. Bugün genel olarak nasıl geçiyor senin için?"
            listOf("yoruldum", "stres", "bunal", "moralim", "yalnız", "tükendim").any { lower.contains(it) } ->
                "Seni çok iyi anlıyorum; bazen üst üste gelen şeyler insanın enerjisini gerçekten tüketebiliyor. Kendine yüklenmeyi bırak, her şeyi bir anda çözmek zorunda değilsin. Ne oldu bugün, anlatmak ister misin?"
            listOf("selam", "merhaba", "nasılsın", "naber", "alo", "sesim geliyor mu").any { lower.contains(it) } ->
                "Selam dostum, sesin gayet net geliyor ve seni dinliyorum! Günün nasıl geçiyor, bugün hangi konu üzerine sohbet edelim?"
            else ->
                "Söylediğin nokta gayet mantıklı. Konuya bu açıdan yaklaşmak detayları çok daha net görmeyi sağlıyor. Peki bu konuda senin aklındaki asıl soru veya üzerinde durmak istediğin detay ne?"
        }
    }
}

