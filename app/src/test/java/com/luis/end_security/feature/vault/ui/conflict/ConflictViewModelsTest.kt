package com.luis.end_security.feature.vault.ui.conflict

import androidx.lifecycle.SavedStateHandle
import com.luis.end_security.feature.vault.domain.ConflictDetail
import com.luis.end_security.feature.vault.domain.ConflictLoad
import com.luis.end_security.feature.vault.domain.ConflictRow
import com.luis.end_security.feature.vault.domain.KeepVersion
import com.luis.end_security.feature.vault.domain.ResolveResult
import com.luis.end_security.feature.vault.domain.VaultItem
import com.luis.end_security.feature.vault.domain.VersionSide
import com.luis.end_security.feature.vault.ui.FakeConflictRepository
import com.luis.end_security.feature.vault.ui.TestClock
import com.luis.end_security.feature.vault.ui.openedSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

private val At = Instant.ofEpochMilli(1_700_000_000_000L)

private fun vaultItem(name: String, password: String, url: String? = "netflix.com") =
    VaultItem(name, "ana@correo.com", password, url, null, At, At)

private fun detail(id: UUID, local: VersionSide, remote: VersionSide) =
    ConflictDetail(id, local, remote, 3, 5, 5, 1, null, At)

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val session = openedSession(TestClock())
    private val conflicts = FakeConflictRepository()
    private val id = UUID.randomUUID()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        session.lock()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): ConflictDetailViewModel {
        val viewModel = ConflictDetailViewModel(
            SavedStateHandle(mapOf(ConflictDetailViewModel.ID_ARG to id.toString())), conflicts, session,
        )
        runCurrent()
        return viewModel
    }

    private fun bothLive() = detail(
        id,
        VersionSide.Live(vaultItem("Netflix", "clave-local-1", "netflix.com"), At),
        VersionSide.Live(vaultItem("Netflix", "clave-remota-22", "netflix.com/mx"), At),
    )

    @Test
    fun `carga las dos versiones, marca lo que difiere y propone la de este telefono`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val state = viewModel().state.value

        assertEquals(ConflictStatus.Ready, state.status)
        assertEquals("Netflix", state.name)
        assertEquals(KeepVersion.Local, state.selected)
        assertFalse(state.hasDeletion)
        val local = state.local!!
        val remote = state.remote!!
        assertEquals(listOf(FieldKind.Name, FieldKind.Username, FieldKind.Site), local.fields.map { it.kind })
        assertFalse(local.fields.first { it.kind == FieldKind.Name }.differs)
        assertTrue(local.fields.first { it.kind == FieldKind.Site }.differs)
        assertTrue(remote.fields.first { it.kind == FieldKind.Site }.differs)
        assertTrue("las contraseñas difieren", local.passwordDiffers && remote.passwordDiffers)
        assertEquals(13, local.passwordLength)
        assertEquals(15, remote.passwordLength)
        assertNull("oculta por omisión", local.shownPassword)
    }

    @Test
    fun `el ojo muestra la contrasena de ese lado y volver a primer plano las oculta`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val viewModel = viewModel()

        viewModel.onToggleLocalPassword()
        assertEquals("clave-local-1", viewModel.state.value.local!!.shownPassword)
        assertNull(viewModel.state.value.remote!!.shownPassword)
        viewModel.onToggleRemotePassword()
        assertEquals("clave-remota-22", viewModel.state.value.remote!!.shownPassword)

        viewModel.onHidePasswords()
        assertNull(viewModel.state.value.local!!.shownPassword)
        assertNull(viewModel.state.value.remote!!.shownPassword)

        viewModel.onToggleLocalPassword()
        viewModel.onToggleLocalPassword()
        assertNull("segundo toque la oculta", viewModel.state.value.local!!.shownPassword)
    }

    @Test
    fun `conservar la elegida resuelve y cierra la pantalla`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val viewModel = viewModel()

        viewModel.onSelect(KeepVersion.Remote)
        viewModel.onKeep()
        runCurrent()

        assertEquals(KeepVersion.Remote, conflicts.resolved.single().second)
        assertEquals(ConflictDetailEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `una sola resolucion a la vez aunque se toque dos veces`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val viewModel = viewModel()

        viewModel.onKeep()
        viewModel.onKeep()
        runCurrent()

        assertEquals(1, conflicts.resolved.size)
    }

    @Test
    fun `si cambio mientras tanto vuelve a cargar y avisa`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val viewModel = viewModel()
        conflicts.resolveResult = ResolveResult.Stale

        viewModel.onKeep()
        runCurrent()

        assertEquals(2, conflicts.loads)
        assertEquals(ResolveError.Stale, viewModel.state.value.error)
        assertEquals(ConflictStatus.Ready, viewModel.state.value.status)
        assertNull(withTimeoutOrNull(50) { viewModel.effects.first() })
    }

    @Test
    fun `si no se puede conservar muestra el error y deja intentar de nuevo`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val viewModel = viewModel()
        conflicts.resolveResult = ResolveResult.Unavailable

        viewModel.onKeep()
        runCurrent()

        assertEquals(ResolveError.Unavailable, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isResolving)
    }

    @Test
    fun `sin red muestra el error y Reintentar vuelve a pedir la version del servidor`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Offline
        val viewModel = viewModel()
        assertEquals(ConflictStatus.Offline, viewModel.state.value.status)

        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        viewModel.load()
        runCurrent()
        assertEquals(ConflictStatus.Ready, viewModel.state.value.status)
    }

    @Test
    fun `un elemento que ya no esta en conflicto cierra la pantalla`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Gone
        val viewModel = viewModel()
        assertEquals(ConflictDetailEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `una eliminacion remota se resuelve con botones y propone quedarse con lo del otro lado si fue aqui`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(
            detail(id, VersionSide.Live(vaultItem("Netflix", "clave-local-1"), At), VersionSide.Deleted(At)),
        )
        val state = viewModel().state.value
        assertTrue(state.hasDeletion)
        assertEquals("el nombre sale de la versión que se puede leer", "Netflix", state.name)
        assertEquals(SideKind.Deleted, state.remote!!.kind)

        conflicts.loadResult = ConflictLoad.Ready(
            detail(id, VersionSide.Deleted(At), VersionSide.Live(vaultItem("Netflix", "clave-remota-22"), At)),
        )
        val local = viewModel().state.value
        assertEquals("si aquí se eliminó, lo natural es la otra", KeepVersion.Remote, local.selected)
    }

    @Test
    fun `una version ilegible se muestra como tal y no tumba la pantalla`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(
            detail(id, VersionSide.Live(vaultItem("Netflix", "clave-local-1"), At), VersionSide.Unreadable(At)),
        )
        val state = viewModel().state.value
        assertEquals(SideKind.Unreadable, state.remote!!.kind)
        assertTrue(state.remote!!.fields.isEmpty())
    }

    @Test
    fun `al bloquear se vacia todo lo descifrado`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val viewModel = viewModel()
        viewModel.onToggleLocalPassword()

        session.lock()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(ConflictStatus.Gone, state.status)
        assertNull(state.local)
        assertNull(state.remote)
        viewModel.onKeep()
        runCurrent()
        assertTrue("sin versiones no se resuelve nada", conflicts.resolved.isEmpty())
    }

    @Test
    fun `el estado no imprime nombres ni contrasenas`() = runTest(dispatcher) {
        conflicts.loadResult = ConflictLoad.Ready(bothLive())
        val viewModel = viewModel()
        viewModel.onToggleLocalPassword()
        val state = viewModel.state.value
        val printed = state.toString() + state.local.toString() + state.remote.toString() +
            state.local!!.fields.joinToString { it.toString() }
        assertFalse(printed.contains("Netflix"))
        assertFalse(printed.contains("clave-"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val conflicts = FakeConflictRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): ConflictListViewModel {
        val viewModel = ConflictListViewModel(conflicts)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private fun row(name: String?) = ConflictRow(UUID.randomUUID(), name, At)

    @Test
    fun `lista los conflictos con su inicial y tocar uno lo abre`() = runTest(dispatcher) {
        val netflix = row("Netflix")
        conflicts.conflicts.value = listOf(netflix, row(null))
        val viewModel = viewModel()

        val rows = viewModel.state.value.rows
        assertEquals(2, rows.size)
        assertEquals("N", rows[0].initial)
        assertEquals("?", rows[1].initial)

        viewModel.onRowClick(rows[0])
        runCurrent()
        assertEquals(ConflictListEffect.Open(netflix.id), viewModel.effects.first())
    }

    @Test
    fun `quedo resuelta se muestra 2,5 segundos y se quita`() = runTest(dispatcher) {
        conflicts.conflicts.value = listOf(row("Spotify"))
        val viewModel = viewModel()

        conflicts.noticeResolved("Netflix")
        runCurrent()
        assertEquals("Netflix", viewModel.state.value.resolvedName)

        advanceTimeBy(ConflictListViewModel.NOTICE_MILLIS - 1)
        runCurrent()
        assertEquals("Netflix", viewModel.state.value.resolvedName)

        advanceTimeBy(1)
        runCurrent()
        assertNull(viewModel.state.value.resolvedName)
    }

    @Test
    fun `al resolver el ultimo la pantalla vuelve a la boveda`() = runTest(dispatcher) {
        conflicts.conflicts.value = listOf(row("Netflix"))
        val viewModel = viewModel()

        conflicts.noticeResolved("Netflix")
        conflicts.conflicts.value = emptyList()
        runCurrent()

        assertEquals(ConflictListEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `el estado no imprime nombres`() = runTest(dispatcher) {
        conflicts.conflicts.value = listOf(row("Secreto-Banco"))
        val state = viewModel().state.value
        assertFalse(state.toString().contains("Secreto"))
        assertFalse(state.rows.joinToString { it.toString() }.contains("Secreto"))
    }
}
