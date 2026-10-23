package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.common.ApplicationScope
import com.luis.end_security.core.crypto.GeneratorOptions
import com.luis.end_security.core.crypto.PasswordGenerator
import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.vault.domain.GeneratorRepository
import com.luis.end_security.feature.vault.domain.PasswordHandoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneratorRepositoryImpl @Inject constructor(
    private val settings: SettingsStore,
) : GeneratorRepository {

    private val generator = PasswordGenerator()

    override val options: Flow<GeneratorOptions> = settings.generatorOptions

    override suspend fun saveOptions(options: GeneratorOptions) = settings.setGeneratorOptions(options.sanitized())

    override fun generate(options: GeneratorOptions): CharArray = generator.generate(options)
}

/** Una sola copia en memoria, que se borra al tomarla o al bloquear la bóveda. */
@Singleton
class PasswordHandoffImpl @Inject constructor(
    private val session: VaultSession,
    @ApplicationScope scope: CoroutineScope,
) : PasswordHandoff {

    private var pending: CharArray? = null

    init {
        scope.launch {
            session.state.collect { state -> if (state is VaultState.Locked) clear() }
        }
    }

    @Synchronized
    override fun put(password: CharArray) {
        // Con la bóveda bloqueada no se guarda nada (el colector de `Locked` ya habría pasado).
        if (session.state.value !is VaultState.Unlocked) return
        pending?.fill('\u0000')
        pending = password.copyOf()
    }

    @Synchronized
    override fun take(): CharArray? = pending.also { pending = null }

    @Synchronized
    override fun clear() {
        pending?.fill('\u0000')
        pending = null
    }

    override fun toString(): String = "PasswordHandoff(***)"
}
