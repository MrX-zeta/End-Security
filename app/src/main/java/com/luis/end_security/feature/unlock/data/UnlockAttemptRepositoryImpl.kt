package com.luis.end_security.feature.unlock.data

import com.luis.end_security.core.datastore.UnlockAttemptStore
import com.luis.end_security.feature.unlock.domain.UnlockAttemptRepository
import javax.inject.Inject
import kotlin.time.Duration

class UnlockAttemptRepositoryImpl @Inject constructor(
    private val store: UnlockAttemptStore,
) : UnlockAttemptRepository {

    override suspend fun remainingLockout(): Duration = store.remainingLockout()

    override suspend fun recordFailure(): Duration = store.recordFailure()

    override suspend fun recordSuccess() = store.recordSuccess()
}
