package com.luis.end_security.feature.health.data

import com.luis.end_security.core.common.ApplicationScope
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.database.dao.HealthSnapshotDao
import com.luis.end_security.core.session.VaultLockedException
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.BreachApi
import com.luis.end_security.feature.health.domain.BreachLookup
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthRepository
import com.luis.end_security.feature.health.domain.HealthState
import com.luis.end_security.feature.health.domain.SnapshotPoint
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.VaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Salud de la bóveda (OP-09, OP-10, OP-44, OP-45; DEC-20, DEC-21, DEC-54, DEC-55). Las contraseñas se descifran **solo
 * mientras dura el análisis** (en `Dispatchers.Default`) y se descartan al terminar: el estado que se queda en memoria
 * no las tiene. Los dañados y los de versión desconocida no cuentan. HIBP: un prefijo por contraseña distinta, en serie.
 */
@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val vault: VaultRepository,
    private val session: VaultSession,
    private val breachApi: BreachApi,
    private val cache: BreachCache,
    private val snapshots: SnapshotStore,
    private val snapshotDao: HealthSnapshotDao,
    private val estimator: PasswordStrengthEstimator,
    private val clock: DeviceClock,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) : HealthRepository {

    private val _state = MutableStateFlow(HealthState())
    override val state: StateFlow<HealthState> = _state.asStateFlow()

    private val mutex = Mutex()

    // Qué había en la bóveda en el último análisis (ids y `local_seq`): si no cambió, no se repite.
    private var lastSignature: Set<Pair<UUID, Long>>? = null
    private var offline = false

    // Cada bloqueo sube la «época»: un análisis que empezó antes ya no publica su resultado (INV-07).
    @Volatile
    private var epoch = 0
    private var unlockJob: Job? = null
    private var lastAttemptAt = 0L

    init {
        scope.launch {
            var previous: VaultState = session.state.value
            session.state.collect { current ->
                when {
                    current is VaultState.Locked -> {
                        // Sin esperar a nada: se corta el trabajo en curso y se vacía el estado.
                        epoch++
                        unlockJob?.cancel()
                        lastSignature = null
                        offline = false
                        lastAttemptAt = 0
                        _state.value = HealthState()
                    }
                    previous is VaultState.Locked && current is VaultState.Unlocked -> {
                        // Al desbloquear: análisis local y, como máximo una vez al día, HIBP (DEC-21). Aparte del colector,
                        // para que un bloqueo no espere a la red.
                        unlockJob = scope.launch {
                            refresh()
                            val last = _state.value.lastCheckedAt
                            if (last == null || Duration.between(last, now()).toMillis() > DAY_MILLIS) checkBreaches(force = false)
                        }
                    }
                }
                previous = current
            }
        }
    }

    private fun now(): Instant = Instant.ofEpochMilli(clock.wallMillis())

    override suspend fun refresh() {
        try {
            mutex.withLock {
                val startEpoch = epoch
                val userId = unlockedUser() ?: return
                // La firma sale de los resúmenes (sin contraseñas): solo se descifra todo si algo cambió.
                val signature = vault.observeSummaries().first().map { it.id to it.localSeq }.toSet()
                if (signature == lastSignature && _state.value.report != null) return
                val entries = readable() ?: return
                analyze(entries, signature, userId, startEpoch)
            }
        } catch (_: VaultLockedException) {
            // Se bloqueó a mitad: no hay nada que publicar.
        }
    }

    override suspend fun open() {
        refresh()
        val noCheckYet = _state.value.lastCheckedAt == null && _state.value.report?.items?.isNotEmpty() == true
        // Sin red o con el servicio limitando no se insiste en cada apertura: 5 min entre intentos.
        if (noCheckYet && clock.wallMillis() - lastAttemptAt > RETRY_MILLIS) checkBreaches(force = false)
    }

    override suspend fun checkBreaches(force: Boolean) {
        try {
            mutex.withLock {
                val startEpoch = epoch
                val userId = unlockedUser() ?: return
                val entries = readable() ?: return
                lastAttemptAt = clock.wallMillis()
                _state.value = _state.value.copyWith(checking = true)
                try {
                    val cached = cache.readAll(userId)
                    val nowMillis = clock.wallMillis()
                    val due = entries.filter { entry ->
                        val hit = cached[entry.id.toString()]
                        force || hit == null || hit.checkedLocalSeq != entry.localSeq || nowMillis - hit.checkedAtMillis > STALE_MILLIS
                    }
                    // Un prefijo por contraseña distinta (las repetidas comparten consulta), en serie.
                    val byPassword = due.groupBy { it.item.password }
                    offline = false
                    for ((password, group) in byPassword) {
                        if (epoch != startEpoch) return
                        val count = when (val result = breachApi.lookup(password)) {
                            is BreachLookup.Found -> result.count
                            BreachLookup.Clean -> 0
                            BreachLookup.Offline -> {
                                offline = true
                                break
                            }
                            // El servicio no responde bien (p. ej. limitación): se deja pendiente y no se insiste.
                            BreachLookup.Unavailable -> break
                        }
                        if (epoch != startEpoch) return
                        for (entry in group) cache.write(entry.id, userId, entry.localSeq, count, clock.wallMillis())
                    }
                    if (epoch != startEpoch) return
                    analyze(entries, entries.map { it.id to it.localSeq }.toSet(), userId, startEpoch)
                } finally {
                    if (epoch == startEpoch) _state.value = _state.value.copyWith(checking = false)
                }
            }
        } catch (_: VaultLockedException) {
            // Se bloqueó a mitad: el estado ya se vació.
        }
    }

    private fun unlockedUser(): UUID? = (session.state.value as? VaultState.Unlocked)?.userId

    /** Los elementos legibles (con contraseña) o `null` con la bóveda bloqueada. Dañados y de otra versión no entran. */
    private suspend fun readable(): List<ReadableEntry>? {
        if (session.state.value !is VaultState.Unlocked) return null
        return vault.observeEntries().first().filterIsInstance<ReadableEntry>()
    }

    private suspend fun analyze(entries: List<ReadableEntry>, signature: Set<Pair<UUID, Long>>, userId: UUID, startEpoch: Int) {
        val cached = cache.readAll(userId)
        val report = withContext(dispatcher) {
            val inputs = entries.map { entry ->
                val hit = cached[entry.id.toString()]?.takeIf { it.checkedLocalSeq == entry.localSeq }
                AnalysisItem(entry.id, entry.item.name, entry.item.password, entry.item.passwordChangedAt, hit?.count)
            }
            HealthAnalyzer.analyze(inputs, now(), estimator)
        }
        // Si se bloqueó mientras tanto, el resultado no se publica.
        if (epoch != startEpoch || session.state.value !is VaultState.Unlocked) return
        lastSignature = signature
        val lastChecked = cached.values.maxOfOrNull { it.checkedAtMillis }?.let { Instant.ofEpochMilli(it) }
        _state.value = HealthState(report, _state.value.checking, lastChecked, offline)
        // OP-45: una foto por día local, con los totales (nunca contraseñas), reemplazando la de hoy.
        report.totals?.let { totals ->
            val day = now().atZone(ZoneId.systemDefault()).toLocalDate()
            snapshots.save(day, userId, totals, clock.wallMillis())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProgress(): Flow<List<SnapshotPoint>> = session.state.flatMapLatest { state ->
        when (state) {
            VaultState.Locked -> flowOf(emptyList())
            is VaultState.Unlocked -> snapshotDao.observeAll().map { rows ->
                try {
                    snapshots.readAll(rows, state.userId)
                } catch (_: VaultLockedException) {
                    emptyList()
                }
            }
        }
    }

    private fun HealthState.copyWith(checking: Boolean) = HealthState(report, checking, lastCheckedAt, offline)

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val STALE_MILLIS = 7L * DAY_MILLIS
        const val RETRY_MILLIS = 5L * 60 * 1000
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class HealthDataModule {

    @dagger.Binds
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository

    @dagger.Binds
    abstract fun bindBreachApi(impl: HibpClient): BreachApi
}
