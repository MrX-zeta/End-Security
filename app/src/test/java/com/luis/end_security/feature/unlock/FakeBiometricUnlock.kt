package com.luis.end_security.feature.unlock

import com.luis.end_security.feature.unlock.domain.BiometricPreparation
import com.luis.end_security.feature.unlock.domain.BiometricResult
import com.luis.end_security.feature.unlock.domain.BiometricUnlock
import kotlinx.coroutines.flow.MutableStateFlow
import javax.crypto.Cipher

/** [BiometricUnlock] programable para las pruebas de los ViewModels. */
class FakeBiometricUnlock : BiometricUnlock {
    val enabledFlow = MutableStateFlow(false)
    override val enabled = enabledFlow

    var enablePreparation: BiometricPreparation = BiometricPreparation.Failed
    var unlockPreparation: BiometricPreparation = BiometricPreparation.NotEnabled
    var enableResult = BiometricResult.Done
    var unlockResult = BiometricResult.Done

    var prepareEnableCalls = 0
    var completeEnableCalls = 0
    var disableCalls = 0
    var prepareUnlockCalls = 0
    var completeUnlockCalls = 0

    override suspend fun prepareEnable(): BiometricPreparation {
        prepareEnableCalls++
        return enablePreparation
    }

    override suspend fun completeEnable(cipher: Cipher): BiometricResult {
        completeEnableCalls++
        if (enableResult == BiometricResult.Done) enabledFlow.value = true
        return enableResult
    }

    override suspend fun disable() {
        disableCalls++
        enabledFlow.value = false
    }

    override suspend fun prepareUnlock(): BiometricPreparation {
        prepareUnlockCalls++
        return unlockPreparation
    }

    override suspend fun completeUnlock(cipher: Cipher): BiometricResult {
        completeUnlockCalls++
        return unlockResult
    }
}
