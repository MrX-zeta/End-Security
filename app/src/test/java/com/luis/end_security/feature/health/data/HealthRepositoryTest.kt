package com.luis.end_security.feature.health.data

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.database.dao.BreachCheckDao
import com.luis.end_security.core.database.dao.HealthSnapshotDao
import com.luis.end_security.core.database.entity.BreachCheckEntity
import com.luis.end_security.core.database.entity.HealthSnapshotEntity
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.BreachApi
import com.luis.end_security.feature.health.domain.BreachLookup
import com.luis.end_security.feature.health.domain.RiskLevel
import com.luis.end_security.feature.vault.domain.DamagedEntry
import com.luis.end_security.feature.vault.domain.UnsupportedEntry
import com.luis.end_security.feature.vault.ui.FakeVaultRepository
import com.luis.end_security.feature.vault.ui.item
import com.luis.end_security.feature.vault.ui.openedSession
import com.luis.end_security.feature.vault.ui.readable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private class FakeBreachDao : BreachCheckDao() {
    val rows = LinkedHashMap<String, BreachCheckEntity>()

    override suspend fun upsert(check: BreachCheckEntity) {
        rows[check.itemId] = check
    }

    override suspend fun get(itemId: String): BreachCheckEntity? = rows[itemId]

    override suspend fun getAll(): List<BreachCheckEntity> = rows.values.toList()
}

private class FakeSnapshotDao : HealthSnapshotDao() {
    val rows = MutableStateFlow<Map<String, HealthSnapshotEntity>>(emptyMap())

    override suspend fun upsert(snapshot: HealthSnapshotEntity) {
        rows.value = rows.value + (snapshot.day to snapshot)
    }

    override fun observeAll(): Flow<List<HealthSnapshotEntity>> = rows.map { it.values.sortedBy { row -> row.day } }

    override suspend fun deleteBefore(firstDayToKeep: String) {
        rows.value = rows.value.filterKeys { it >= firstDayToKeep }
    }
}

/** HIBP de mentira: dice cuántas veces aparece cada contraseña y anota cada consulta. */
private class FakeBreachApi : BreachApi {
    val counts = HashMap<String, Int>()
    val lookups = mutableListOf<String>()
    var result: BreachLookup? = null
    var onLookup: (() -> Unit)? = null

    override suspend fun lookup(password: String): BreachLookup {
        lookups += password
        onLookup?.invoke()
        result?.let { return it }
        val count = counts[password] ?: 0
        return if (count > 0) BreachLookup.Found(count) else BreachLookup.Clean
    }
}

class HealthRepositoryTest {

    private var wall = LocalDate.of(2026, 10, 22).atTime(10, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val clock = object : DeviceClock {
        override fun wallMillis() = wall
        override fun elapsedRealtimeMillis() = wall
    }
    private val session: VaultSession = openedSession(object : DeviceClock {
        override fun wallMillis() = wall
        override fun elapsedRealtimeMillis() = wall
    })
    private val vault = FakeVaultRepository()
    private val api = FakeBreachApi()
    private val breachDao = FakeBreachDao()
    private val snapshotDao = FakeSnapshotDao()
    private val job = Job()
    private val estimator = object : PasswordStrengthEstimator {
        // «clave-» → débil; cualquier otra → fuerte.
        override fun score(password: CharSequence): Int = if (password.startsWith("clave-")) 0 else 4
    }
    private val repository = HealthRepositoryImpl(
        vault, session, api, BreachCache(breachDao, session), SnapshotStore(snapshotDao, session), snapshotDao,
        estimator, clock, Dispatchers.Unconfined, CoroutineScope(Dispatchers.Unconfined + job),
    )

    @After
    fun tearDown() {
        session.lock()
        job.cancel()
    }

    private fun recent() = Instant.ofEpochMilli(wall)

    @Test
    fun `revisa una contraseña distinta una sola vez aunque se repita y la marca critica`() = runBlocking {
        api.counts["Filtrada-Segura-1"] = 9_000
        vault.entries.value = listOf(
            readable("A", localSeq = 1, item = item("A", password = "Filtrada-Segura-1", changedAt = recent())),
            readable("B", localSeq = 1, item = item("B", password = "Filtrada-Segura-1", changedAt = recent())),
            readable("C", localSeq = 1, item = item("C", password = "Unica-Y-Larga-77", changedAt = recent())),
        )

        repository.checkBreaches(force = false)

        assertEquals("un prefijo por contraseña distinta", 2, api.lookups.size)
        val report = repository.state.value.report!!
        assertEquals(2, report.breached.size)
        assertTrue(report.items.filter { it.breached }.all { it.level == RiskLevel.Critical })
        assertEquals(0, report.pendingBreachCount)
        assertNotNull(repository.state.value.lastCheckedAt)
        assertFalse(repository.state.value.offline)
    }

    @Test
    fun `la cache evita volver a consultar y se repite si cambio el local_seq`() = runBlocking {
        val id = UUID.randomUUID()
        val other = UUID.randomUUID()
        vault.entries.value = listOf(
            readable("A", id, 1, item("A", password = "Uno-Largo-123", changedAt = recent())),
            readable("B", other, 1, item("B", password = "Dos-Largo-456", changedAt = recent())),
        )
        repository.checkBreaches(false)
        assertEquals(2, api.lookups.size)

        repository.checkBreaches(false)
        assertEquals("todo en caché y vigente", 2, api.lookups.size)

        // La contraseña de A cambió: su `local_seq` ya no coincide.
        vault.entries.value = listOf(
            readable("A", id, 2, item("A", password = "Uno-Cambiada-789", changedAt = recent())),
            readable("B", other, 1, item("B", password = "Dos-Largo-456", changedAt = recent())),
        )
        repository.checkBreaches(false)
        assertEquals(3, api.lookups.size)
        assertEquals("Uno-Cambiada-789", api.lookups.last())
    }

    @Test
    fun `a los 7 dias vuelve a revisar todo y con forzar tambien`() = runBlocking {
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())))
        repository.checkBreaches(false)
        assertEquals(1, api.lookups.size)

        wall += Duration.ofDays(6).toMillis()
        repository.checkBreaches(false)
        assertEquals("a los 6 días sigue vigente", 1, api.lookups.size)

        wall += Duration.ofDays(2).toMillis()
        repository.checkBreaches(false)
        assertEquals("a los 8 días ya venció", 2, api.lookups.size)

        repository.checkBreaches(force = true)
        assertEquals("forzar vuelve a preguntar", 3, api.lookups.size)
    }

    @Test
    fun `sin red lo no revisado queda pendiente y no cuenta como filtrado`() = runBlocking {
        api.result = BreachLookup.Offline
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("password123", password = "password123", changedAt = recent())))

        repository.checkBreaches(false)

        val state = repository.state.value
        assertTrue(state.offline)
        assertNull("nunca se revisó", state.lastCheckedAt)
        val health = state.report!!.items.single()
        assertTrue(health.breachPending)
        assertFalse(health.breached)
        assertEquals(1, api.lookups.size)
    }

    @Test
    fun `un servicio que no responde bien no insiste ni marca filtrado`() = runBlocking {
        api.result = BreachLookup.Unavailable
        vault.entries.value = listOf(
            readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())),
            readable("B", localSeq = 1, item = item("B", password = "Dos-Largo-456", changedAt = recent())),
        )
        repository.checkBreaches(false)
        assertEquals(1, api.lookups.size)
        assertFalse(repository.state.value.offline)
        assertEquals(2, repository.state.value.report!!.pendingBreachCount)
    }

    @Test
    fun `los daniados y los de version desconocida no cuentan`() = runBlocking {
        vault.entries.value = listOf(
            readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())),
            DamagedEntry(UUID.randomUUID(), 1),
            UnsupportedEntry(UUID.randomUUID(), 1, 2),
        )
        repository.refresh()
        assertEquals(1, repository.state.value.report!!.items.size)
    }

    @Test
    fun `refresh no repite el analisis si la boveda no cambio`() = runBlocking {
        val counting = object : PasswordStrengthEstimator {
            var calls = 0

            override fun score(password: CharSequence): Int {
                calls++
                return 4
            }
        }
        val repo = HealthRepositoryImpl(
            vault, session, api, BreachCache(breachDao, session), SnapshotStore(snapshotDao, session), snapshotDao,
            counting, clock, Dispatchers.Unconfined, CoroutineScope(Dispatchers.Unconfined + job),
        )
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("A", changedAt = recent())))

        repo.refresh()
        repo.refresh()
        assertEquals(1, counting.calls)

        vault.entries.value = vault.entries.value + readable("B", localSeq = 1, item = item("B", password = "Otra-Larga-1", changedAt = recent()))
        repo.refresh()
        assertEquals("al cambiar la bóveda se reanaliza", 3, counting.calls)
    }

    @Test
    fun `la foto diaria es una por dia, reemplaza la de hoy y guarda solo totales`() = runBlocking {
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())))
        repository.refresh()
        repository.checkBreaches(true)
        assertEquals("dos análisis el mismo día = una foto", 1, snapshotDao.rows.value.size)

        wall += Duration.ofDays(1).toMillis()
        vault.entries.value = vault.entries.value + readable("B", localSeq = 1, item = item("B", password = "Dos-Largo-456", changedAt = recent()))
        repository.refresh()
        assertEquals(2, snapshotDao.rows.value.size)

        val points = repository.observeProgress().first()
        assertEquals(2, points.size)
        assertEquals(1, points[0].totals.total)
        assertEquals(2, points[1].totals.total)
        // Cifrada: sin números ni nombres legibles en la fila.
        val raw = snapshotDao.rows.value.values.first().let { String(it.nonce + it.ciphertext, Charsets.ISO_8859_1) }
        assertFalse(raw.contains("score") || raw.contains("total"))
    }

    @Test
    fun `se borran las fotos de mas de 12 meses`() = runBlocking {
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())))
        repository.refresh()
        val oldDay = LocalDate.of(2026, 10, 22).minusMonths(13).toString()
        val keptDay = LocalDate.of(2026, 10, 22).minusMonths(11).toString()
        snapshotDao.upsert(HealthSnapshotEntity(oldDay, 1, ByteArray(12), ByteArray(40), 0))
        snapshotDao.upsert(HealthSnapshotEntity(keptDay, 1, ByteArray(12), ByteArray(40), 0))

        wall += Duration.ofDays(1).toMillis()
        vault.entries.value = vault.entries.value + readable("B", localSeq = 1, item = item("B", password = "Dos-Largo-456", changedAt = recent()))
        repository.refresh()

        assertFalse(snapshotDao.rows.value.containsKey(oldDay))
        assertTrue(snapshotDao.rows.value.containsKey(keptDay))
    }

    @Test
    fun `la cache de filtraciones esta cifrada y no abre con otro usuario`() = runBlocking {
        api.counts["Filtrada-Segura-1"] = 4_242
        val id = UUID.randomUUID()
        vault.entries.value = listOf(readable("A", id, 1, item("A", password = "Filtrada-Segura-1", changedAt = recent())))
        repository.checkBreaches(false)

        val row = breachDao.rows.getValue(id.toString())
        val raw = String(row.nonce + row.ciphertext, Charsets.ISO_8859_1)
        assertFalse(raw.contains("pwned_count") || raw.contains("4242"))
        assertEquals(1L, row.checkedLocalSeq)

        // La AAD lleva el usuario: con otro usuario no abre y se vuelve a consultar.
        val cache = BreachCache(breachDao, session)
        assertEquals(4_242, cache.readAll(UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")).getValue(id.toString()).count)
        assertTrue(cache.readAll(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `con la boveda bloqueada el estado se vacia y no se analiza`() = runBlocking {
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())))
        repository.refresh()
        assertNotNull(repository.state.value.report)

        session.lock()

        assertNull(repository.state.value.report)
        repository.refresh()
        repository.checkBreaches(false)
        assertNull(repository.state.value.report)
        assertTrue(repository.observeProgress().first().isEmpty())
    }

    @Test
    fun `el estado no guarda ni imprime contrasenas`() = runBlocking {
        vault.entries.value = listOf(readable("Banco", localSeq = 1, item = item("Banco", password = "Secreta-Unica-99", changedAt = recent())))
        repository.refresh()
        val printed = repository.state.value.toString() + repository.state.value.report!!.items.joinToString { it.toString() }
        assertFalse(printed.contains("Secreta"))
    }

    @Test
    fun `open revisa HIBP la primera vez y no la segunda`() = runBlocking {
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())))
        repository.open()
        assertEquals(1, api.lookups.size)
        repository.open()
        assertEquals(1, api.lookups.size)
    }

    @Test
    fun `si se bloquea a mitad de una revision no se publica nada y se corta la red`() = runBlocking {
        vault.entries.value = listOf(
            readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())),
            readable("B", localSeq = 1, item = item("B", password = "Dos-Largo-456", changedAt = recent())),
        )
        api.onLookup = { session.lock() }

        repository.checkBreaches(false)

        assertNull("el informe no reaparece con la bóveda bloqueada", repository.state.value.report)
        assertEquals("después del bloqueo no se hacen más consultas", 1, api.lookups.size)
        assertFalse(repository.state.value.checking)
    }

    @Test
    fun `una cache con las columnas alteradas no se cree y se vuelve a consultar`() = runBlocking {
        val id = UUID.randomUUID()
        vault.entries.value = listOf(readable("A", id, 1, item("A", password = "Uno-Largo-123", changedAt = recent())))
        repository.checkBreaches(false)
        assertEquals(1, api.lookups.size)

        // Alguien con acceso a Room rejuvenece la fila cambiando la hora en claro.
        val row = breachDao.rows.getValue(id.toString())
        breachDao.rows[id.toString()] = BreachCheckEntity(row.itemId, row.checkedLocalSeq, row.nonce, row.ciphertext, row.checkedAt + 1_000_000)

        repository.checkBreaches(false)
        assertEquals("la fila alterada no abre y se consulta otra vez", 2, api.lookups.size)
    }

    @Test
    fun `sin red no se insiste en cada apertura de Salud`() = runBlocking {
        api.result = BreachLookup.Offline
        vault.entries.value = listOf(readable("A", localSeq = 1, item = item("A", password = "Uno-Largo-123", changedAt = recent())))
        repository.open()
        repository.open()
        assertEquals(1, api.lookups.size)
        wall += Duration.ofMinutes(6).toMillis()
        repository.open()
        assertEquals(2, api.lookups.size)
    }
}
