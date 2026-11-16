package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.CharacterPreferencesDataStore
import com.example.data.model.CharacterIdentity
import com.example.data.model.CompanionState
import com.example.data.model.Conversation
import com.example.data.model.IdealSelfProfile
import com.example.data.model.LongTermMemory
import com.example.data.model.Message
import com.example.data.model.UserAccount
import com.example.data.model.UserCompanionDocument
import com.example.data.model.toCreateMap
import com.example.data.model.toUpdateMap
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CompanionRepository(
    private val firestoreDb: FirebaseFirestore? = null,
    private val isGuestMode: Boolean = false,
    appContext: Context? = null
) {

    // Convenience secondary constructor resolving named database ID from string resources
    constructor(context: Context, isGuestMode: Boolean = false) : this(
        firestoreDb = if (isGuestMode) {
            null
        } else {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        },
        isGuestMode = isGuestMode,
        appContext = context.applicationContext
    )

    private val characterPrefs: CharacterPreferencesDataStore? =
        appContext?.let { CharacterPreferencesDataStore(it) }

    private val db: FirebaseFirestore
        get() = firestoreDb ?: throw IllegalStateException("Firestore instance is not required in Guest Mode.")

    companion object {
        const val GUEST_USER_ID = "guest_tester_local"
    }

    private val auth get() = Firebase.auth

    private val guestStateFlow = MutableStateFlow<CompanionState?>(null)
    private val guestConversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
    private val guestMessagesByConversationFlow = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val guestMemoriesFlow = MutableStateFlow<List<LongTermMemory>>(emptyList())

    private fun isGuest(userId: String? = null): Boolean {
        return isGuestMode || userId == GUEST_USER_ID
    }

    private fun requireUserId(): String {
        if (isGuestMode) return GUEST_USER_ID
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. USER & COMPANION PROFILE (/users/{userId}) ---

    fun observeCompanionState(userId: String = requireUserId()): Flow<CompanionState?> = flow {
        if (isGuest(userId)) {
            emitAll(guestStateFlow)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        null
                    } else {
                        snapshot.toObject(
                            UserCompanionDocument::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.toCompanionState()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getOrCreateCompanionState(
        displayName: String = "Dreamer",
        email: String = ""
    ): Result<CompanionState> = runCatching {
        val persistedCustomization = characterPrefs?.readCustomization()

        if (isGuestMode) {
            val existing = guestStateFlow.value
            if (existing != null) {
                val merged = if (persistedCustomization != null) {
                    existing.copy(
                        characterAppearance = persistedCustomization.appearance,
                        clothingAndAccessories = persistedCustomization.clothing,
                        voicePreferences = persistedCustomization.voicePreferences
                    )
                } else {
                    existing
                }
                guestStateFlow.value = merged
                return@runCatching merged
            }
            val guestInitial = CompanionState(
                userAccount = UserAccount(
                    userId = GUEST_USER_ID,
                    displayName = displayName.ifBlank { "Misafir Test Kullanıcısı" },
                    email = email.ifBlank { "guest@idealself.local" },
                    onboardingCompleted = true
                ),
                idealSelfProfile = IdealSelfProfile(
                    coreAspiration = "Sakin özgüven, yaratıcı cesaret ve her gün bilinçli farkındalıkla hareket etmek.",
                    lifePillars = listOf("Zihinsel Berraklık", "Yaratıcı Ustalık", "Özgün Bağlar"),
                    growthGoals = "Net ve samimi iletişim kurmak; anlamlı hedefler için her gün küçük ama kararlı adımlar atmak.",
                    emotionalGrounding = "Baskı altında bile sakin, meraklı ve gerçekçi kalmak.",
                    reflectionStyle = "Samimi, sorgulayıcı ve esprili",
                    quizCompleted = false
                ),
                characterIdentity = CharacterIdentity(
                    companionName = "Nova",
                    archetypeTitle = "Sakin Mimar (The Serene Architect)",
                    backstorySummary = "En yüksek kişisel hedeflerinden doğan 16-bit piksel yol arkadaşın.",
                    greetingStyle = "Selam! Bugün gerçekten neye odaklanmak istiyoruz? Gel fazlalıkları bir kenara bırakıp konuşalım.",
                    signatureCatchphrase = "Her bilinçli piksel kendi başyapıtını inşa eder."
                ),
                characterAppearance = persistedCustomization?.appearance ?: com.example.data.model.CharacterAppearance(),
                clothingAndAccessories = persistedCustomization?.clothing ?: com.example.data.model.ClothingAndAccessories(),
                voicePreferences = persistedCustomization?.voicePreferences ?: com.example.data.model.VoicePreferences(),
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            guestStateFlow.value = guestInitial
            return@runCatching guestInitial
        }

        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existingDoc = snapshot.toObject(
                    UserCompanionDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existingDoc != null) {
                    val cloudState = existingDoc.toCompanionState()
                    val finalState = if (persistedCustomization != null) {
                        cloudState.copy(
                            characterAppearance = persistedCustomization.appearance,
                            clothingAndAccessories = persistedCustomization.clothing,
                            voicePreferences = persistedCustomization.voicePreferences
                        )
                    } else {
                        characterPrefs?.saveAppearanceAndClothing(
                            cloudState.characterAppearance,
                            cloudState.clothingAndAccessories,
                            cloudState.voicePreferences
                        )
                        cloudState
                    }
                    return@runCatching finalState
                }
            }
            val initial = CompanionState(
                userAccount = UserAccount(
                    userId = uid,
                    displayName = displayName.ifBlank { "Dreamer" },
                    email = email,
                    onboardingCompleted = false
                ),
                characterAppearance = persistedCustomization?.appearance ?: com.example.data.model.CharacterAppearance(),
                clothingAndAccessories = persistedCustomization?.clothing ?: com.example.data.model.ClothingAndAccessories(),
                voicePreferences = persistedCustomization?.voicePreferences ?: com.example.data.model.VoicePreferences()
            )
            docRef.set(initial.toCreateMap(uid)).await()
            characterPrefs?.saveAppearanceAndClothing(
                initial.characterAppearance,
                initial.clothingAndAccessories,
                initial.voicePreferences
            )
            initial
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getCompanionStateByUserId(targetUserId: String): Result<CompanionState?> = runCatching {
        if (isGuest(targetUserId)) {
            return@runCatching guestStateFlow.value
        }
        val docRef = db.collection("users").document(targetUserId)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) null
            else snapshot.toObject(
                UserCompanionDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toCompanionState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun saveCompanionState(state: CompanionState): Result<Unit> = runCatching {
        characterPrefs?.saveAppearanceAndClothing(
            state.characterAppearance,
            state.clothingAndAccessories,
            state.voicePreferences
        )
        if (isGuestMode) {
            guestStateFlow.value = state.copy(updatedAt = Timestamp.now())
            return@runCatching
        }
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(state.toUpdateMap()).await()
            } else {
                docRef.set(state.toCreateMap(uid)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 2. CONVERSATIONS (/users/{userId}/conversations/{conversationId}) ---

    fun observeConversations(userId: String = requireUserId()): Flow<List<Conversation>> = flow {
        if (isGuest(userId)) {
            emitAll(
                guestConversationsFlow.map { list ->
                    list.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
            )
            return@flow
        }
        val path = "users/$userId/conversations"
        emitAll(
            db.collection("users").document(userId).collection("conversations")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversation(
        title: String,
        mode: String,
        initialPreview: String = ""
    ): Result<String> = runCatching {
        if (isGuestMode) {
            val convId = "guest_conv_${UUID.randomUUID().toString().take(8)}"
            val now = Timestamp.now()
            val newConv = Conversation(
                id = convId,
                userId = GUEST_USER_ID,
                title = title.take(120).ifBlank { "Stüdyo Sohbeti" },
                mode = if (mode == "VOICE") "VOICE" else "TEXT",
                lastMessagePreview = initialPreview.take(500),
                messageCount = 0L,
                createdAt = now,
                updatedAt = now
            )
            guestConversationsFlow.value = listOf(newConv) + guestConversationsFlow.value
            return@runCatching convId
        }
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("conversations").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.take(120).ifBlank { "Studio Conversation" },
            "mode" to if (mode == "VOICE") "VOICE" else "TEXT",
            "lastMessagePreview" to initialPreview.take(500),
            "messageCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateConversationSummary(
        conversationId: String,
        title: String? = null,
        lastMessagePreview: String,
        messageCount: Long
    ): Result<Unit> = runCatching {
        if (isGuestMode) {
            val now = Timestamp.now()
            guestConversationsFlow.value = guestConversationsFlow.value.map { conv ->
                if (conv.id == conversationId) {
                    conv.copy(
                        title = if (!title.isNullOrBlank()) title.take(120) else conv.title,
                        lastMessagePreview = lastMessagePreview.take(500),
                        messageCount = messageCount.coerceIn(0L, 10000L),
                        updatedAt = now
                    )
                } else {
                    conv
                }
            }
            return@runCatching
        }
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        val updates = mutableMapOf<String, Any>(
            "lastMessagePreview" to lastMessagePreview.take(500),
            "messageCount" to messageCount.coerceIn(0L, 10000L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!title.isNullOrBlank()) {
            updates["title"] = title.take(120)
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        if (isGuestMode) {
            guestConversationsFlow.value = guestConversationsFlow.value.filterNot { it.id == conversationId }
            guestMessagesByConversationFlow.value = guestMessagesByConversationFlow.value - conversationId
            return@runCatching
        }
        val uid = requireUserId()
        val convRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        try {
            val messages = convRef.collection("messages").get().await()
            for (msg in messages.documents) {
                msg.reference.delete().await()
            }
            convRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            throw e
        }
    }

    // --- 3. MESSAGES (/users/{userId}/conversations/{conversationId}/messages/{messageId}) ---

    fun observeMessages(
        conversationId: String,
        userId: String = requireUserId()
    ): Flow<List<Message>> = flow {
        if (isGuest(userId)) {
            emitAll(
                guestMessagesByConversationFlow.map { map ->
                    map[conversationId].orEmpty().sortedBy { it.createdAt ?: Timestamp.now() }
                }
            )
            return@flow
        }
        val path = "users/$userId/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Message::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMessage(
        conversationId: String,
        sender: String,
        content: String,
        emotionTag: String = "warm_smile"
    ): Result<String> = runCatching {
        if (isGuestMode) {
            val msgId = "guest_msg_${UUID.randomUUID().toString().take(8)}"
            val newMsg = Message(
                id = msgId,
                userId = GUEST_USER_ID,
                conversationId = conversationId,
                sender = if (sender == "COMPANION") "COMPANION" else "USER",
                content = content.take(8000).ifBlank { "..." },
                emotionTag = emotionTag.take(40).ifBlank { "warm_smile" },
                createdAt = Timestamp.now()
            )
            val currentList = guestMessagesByConversationFlow.value[conversationId].orEmpty()
            guestMessagesByConversationFlow.value =
                guestMessagesByConversationFlow.value + (conversationId to (currentList + newMsg))
            return@runCatching msgId
        }
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document()
        val payload = mapOf(
            "userId" to uid,
            "conversationId" to conversationId,
            "sender" to if (sender == "COMPANION") "COMPANION" else "USER",
            "content" to content.take(8000).ifBlank { "..." },
            "emotionTag" to emotionTag.take(40).ifBlank { "warm_smile" },
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- 4. LONG-TERM MEMORIES (/users/{userId}/memories/{memoryId}) ---

    fun observeMemories(userId: String = requireUserId()): Flow<List<LongTermMemory>> = flow {
        if (isGuest(userId)) {
            emitAll(
                guestMemoriesFlow.map { list ->
                    list.sortedWith(
                        compareByDescending<LongTermMemory> { it.importance }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
            )
            return@flow
        }
        val path = "users/$userId/memories"
        emitAll(
            db.collection("users").document(userId).collection("memories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LongTermMemory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<LongTermMemory> { it.importance }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMemory(
        category: String,
        summary: String,
        importance: Int = 3,
        sourceConversationId: String = ""
    ): Result<String> = runCatching {
        val validCategory = if (category in listOf("GOAL", "VALUE", "PREFERENCE", "MILESTONE", "INSIGHT")) {
            category
        } else {
            "INSIGHT"
        }
        if (isGuestMode) {
            val memId = "guest_mem_${UUID.randomUUID().toString().take(8)}"
            val now = Timestamp.now()
            val newMem = LongTermMemory(
                id = memId,
                userId = GUEST_USER_ID,
                category = validCategory,
                summary = summary.take(600).ifBlank { "Important personal reflection." },
                importance = importance.toLong().coerceIn(1L, 5L),
                sourceConversationId = sourceConversationId.take(128),
                createdAt = now,
                updatedAt = now
            )
            guestMemoriesFlow.value = listOf(newMem) + guestMemoriesFlow.value
            return@runCatching memId
        }
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document()
        val payload = mapOf(
            "userId" to uid,
            "category" to validCategory,
            "summary" to summary.take(600).ifBlank { "Important personal reflection." },
            "importance" to importance.toLong().coerceIn(1L, 5L),
            "sourceConversationId" to sourceConversationId.take(128),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateMemory(
        memoryId: String,
        category: String,
        summary: String,
        importance: Int = 3
    ): Result<Unit> = runCatching {
        val validCategory = if (category in listOf("GOAL", "VALUE", "PREFERENCE", "MILESTONE", "INSIGHT")) {
            category
        } else {
            "INSIGHT"
        }
        if (isGuestMode) {
            val now = Timestamp.now()
            guestMemoriesFlow.value = guestMemoriesFlow.value.map { mem ->
                if (mem.id == memoryId) {
                    mem.copy(
                        category = validCategory,
                        summary = summary.take(600).ifBlank { "Updated personal reflection." },
                        importance = importance.toLong().coerceIn(1L, 5L),
                        updatedAt = now
                    )
                } else {
                    mem
                }
            }
            return@runCatching
        }
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        val updates = mapOf(
            "category" to validCategory,
            "summary" to summary.take(600).ifBlank { "Updated personal reflection." },
            "importance" to importance.toLong().coerceIn(1L, 5L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> = runCatching {
        if (isGuestMode) {
            guestMemoriesFlow.value = guestMemoriesFlow.value.filterNot { it.id == memoryId }
            return@runCatching
        }
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAllMemories(): Result<Int> = runCatching {
        if (isGuestMode) {
            val count = guestMemoriesFlow.value.size
            guestMemoriesFlow.value = emptyList()
            return@runCatching count
        }
        val uid = requireUserId()
        val memoriesRef = db.collection("users").document(uid).collection("memories")
        try {
            val snapshot = memoriesRef.get().await()
            var count = 0
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
                count++
            }
            count
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memoriesRef.path)
            throw e
        }
    }

    /**
     * Permanently deletes all user-owned cloud data (all messages, conversations,
     * long-term memories, and the /users/{userId} profile document) to satisfy
     * Google Play & App Store user data deletion requirements.
     */
    suspend fun deleteAllUserData(): Result<Unit> = runCatching {
        characterPrefs?.clearCustomization()
        if (isGuestMode) {
            guestMessagesByConversationFlow.value = emptyMap()
            guestConversationsFlow.value = emptyList()
            guestMemoriesFlow.value = emptyList()
            guestStateFlow.value = null
            return@runCatching
        }
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        try {
            val conversationsSnap = userRef.collection("conversations").get().await()
            for (convDoc in conversationsSnap.documents) {
                val messagesSnap = convDoc.reference.collection("messages").get().await()
                for (msgDoc in messagesSnap.documents) {
                    msgDoc.reference.delete().await()
                }
                convDoc.reference.delete().await()
            }
            val memoriesSnap = userRef.collection("memories").get().await()
            for (memDoc in memoriesSnap.documents) {
                memDoc.reference.delete().await()
            }
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                userRef.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            throw e
        }
    }
}
