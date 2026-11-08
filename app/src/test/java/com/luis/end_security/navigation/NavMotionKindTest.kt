package com.luis.end_security.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Qué transición va entre qué pantallas (tarea 24): la política de `NavMotion`, sin dispositivo. */
class NavMotionKindTest {

    private fun kind(from: String, to: String) = navMotionKind(from, to)

    @Test
    fun `entre pestanas es un fundido sin desplazamiento`() {
        val tabs = listOf(Routes.VAULT, Routes.HEALTH, Routes.GENERATOR, Routes.SETTINGS)
        for (a in tabs) for (b in tabs) if (a != b) assertEquals("$a -> $b", NavMotionKind.TabSwitch, kind(a, b))
    }

    @Test
    fun `avanzar dentro de una pestana es un desplazamiento corto`() {
        assertEquals(NavMotionKind.Push, kind(Routes.SETTINGS, Routes.DEVICES))
        assertEquals(NavMotionKind.Push, kind(Routes.SETTINGS, Routes.ACTIVITY))
        assertEquals(NavMotionKind.Push, kind(Routes.SETTINGS, Routes.CHANGE_PASSWORD))
        assertEquals(NavMotionKind.Push, kind(Routes.VAULT, Routes.vaultItem(UUID.randomUUID()).let { Routes.VAULT_ITEM }))
        assertEquals(NavMotionKind.Push, kind(Routes.VAULT_ITEM, Routes.VAULT_EDIT))
        assertEquals(NavMotionKind.Push, kind(Routes.HEALTH, Routes.HEALTH_BREACHED))
        // Y regresar es el mismo par al revés.
        assertEquals(NavMotionKind.Push, kind(Routes.DEVICES, Routes.SETTINGS))
        assertEquals(NavMotionKind.Push, kind(Routes.VAULT_EDIT, Routes.VAULT_ITEM))
    }

    @Test
    fun `los documentos legales avanzan desde las dos zonas`() {
        assertEquals(NavMotionKind.Push, kind(Routes.SETTINGS, Routes.LEGAL_DOCUMENT))
        assertEquals(NavMotionKind.Push, kind(Routes.SIGN_UP, Routes.LEGAL_DOCUMENT))
    }

    @Test
    fun `bloquear y entrar o salir de sesion es un fundido`() {
        // Bloquear.
        assertEquals(NavMotionKind.StateChange, kind(Routes.VAULT, Routes.UNLOCK))
        assertEquals(NavMotionKind.StateChange, kind(Routes.SETTINGS, Routes.UNLOCK))
        // Desbloquear y la descarga inicial.
        assertEquals(NavMotionKind.StateChange, kind(Routes.UNLOCK, Routes.VAULT))
        assertEquals(NavMotionKind.StateChange, kind(Routes.LOGIN, Routes.VAULT_LOADING))
        assertEquals(NavMotionKind.StateChange, kind(Routes.VAULT_LOADING, Routes.VAULT))
        // Cerrar sesión y cuenta eliminada.
        assertEquals(NavMotionKind.StateChange, kind(Routes.SETTINGS, Routes.LOGIN))
        assertEquals(NavMotionKind.StateChange, kind(Routes.DELETE_ACCOUNT, Routes.ACCOUNT_DELETED))
        assertEquals(NavMotionKind.StateChange, kind(Routes.ACCOUNT_DELETED, Routes.LOGIN))
        // Entrar con 2FA o con código de respaldo desde el acceso.
        assertEquals(NavMotionKind.StateChange, kind(Routes.TWO_FACTOR, Routes.VAULT_LOADING))
    }

    @Test
    fun `el flujo de acceso avanza y regresa como cualquier otro`() {
        assertEquals(NavMotionKind.Push, kind(Routes.ONBOARDING, Routes.SIGN_UP))
        assertEquals(NavMotionKind.Push, kind(Routes.LOGIN, Routes.SIGN_UP))
        assertEquals(NavMotionKind.Push, kind(Routes.LOGIN, Routes.TWO_FACTOR))
        assertEquals(NavMotionKind.Push, kind(Routes.TWO_FACTOR, Routes.BACKUP_CODE))
        assertEquals(NavMotionKind.Push, kind(Routes.SIGN_UP, Routes.VERIFY_EMAIL))
        assertEquals(NavMotionKind.Push, kind(Routes.ONBOARDING, Routes.LOGIN))
    }
}
