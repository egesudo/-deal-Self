package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun getOrCreateAndUpdateCompanionState_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CompanionRepository(firestore)

        val initialResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getOrCreateCompanionState("Alice Starlight", ALICE_EMAIL)
        }
        assertTrue("Expected initial profile load/creation to succeed", initialResult.isSuccess)
        val initialState = initialResult.getOrThrow()
        assertTrue(initialState.characterIdentity.companionName.isNotBlank())

        val targetName = if (initialState.characterIdentity.companionName == "Astra") "Nova" else "Astra"
        val updatedState = initialState.copy(
            characterIdentity = initialState.characterIdentity.copy(companionName = targetName),
            personalityTraits = initialState.personalityTraits.copy(warmth = 95)
        )
        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveCompanionState(updatedState)
        }
        assertTrue("Expected profile update to succeed", saveResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCompanionState(aliceUid).first { it?.characterIdentity?.companionName == targetName }
        }
        assertNotNull(observed)
        assertEquals(95, observed?.personalityTraits?.warmth)
    }

    @Test
    fun createConversationMessagesAndMemories_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CompanionRepository(firestore)

        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversation("Morning Intention", "VOICE", "Starting voice session").getOrThrow()
        }
        assertTrue(convId.isNotEmpty())

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(convId, "USER", "Help me stay calm and focused today.", "determined").getOrThrow()
        }
        assertTrue(msgId.isNotEmpty())

        val memId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMemory("GOAL", "[GOAL] Stay calm and focused during morning deep work.", 4, convId).getOrThrow()
        }
        assertTrue(memId.isNotEmpty())

        // Verify in-place memory update (e.g. opinion shift or decision refinement) passes Firestore rules
        val updateMemResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateMemory(
                memoryId = memId,
                category = "VALUE",
                summary = "[OPINION_SHIFT] Prefers deep evening writing sessions over rushed morning blocks.",
                importance = 5
            )
        }
        assertTrue("Expected memory update to succeed under Firestore security rules", updateMemResult.isSuccess)

        val memories = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMemories(aliceUid).first { list ->
                list.any { it.id == memId && it.summary.contains("OPINION_SHIFT") }
            }
        }
        assertTrue(memories.any { it.summary.contains("evening writing") && it.importance == 5L })

        // Verify full user cloud data deletion (messages, conversations, memories, and user profile) succeeds
        val deleteAllRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteAllUserData()
        }
        assertTrue("Expected full user data deletion to succeed under Firestore rules", deleteAllRes.isSuccess)
    }

    @Test
    fun crossUserAccess_readingAnotherUsersProfile_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CompanionRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getOrCreateCompanionState("Alice", ALICE_EMAIL).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CompanionRepository(firestore)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getCompanionStateByUserId(aliceUid)
        }
        assertTrue("Expected cross-user read to fail", crossReadResult.isFailure)
        val firestoreEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeConversations_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = CompanionRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeConversations(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Expected exception for unauthenticated query", caughtException)
        val firestoreException = checkNotNull(caughtException!!.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caughtException"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@idealself.test"
        const val BOB_EMAIL = "bob@idealself.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
