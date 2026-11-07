package com.luis.end_security.navigation

import com.luis.end_security.core.designsystem.component.BottomNavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Qué destinos llevan la barra inferior persistente y con qué pestaña encendida (tarea 26). */
class TabForRouteTest {

    @Test
    fun `las cuatro raices de pestana`() {
        assertEquals(BottomNavTab.Vault, tabForRoute(Routes.VAULT))
        assertEquals(BottomNavTab.Health, tabForRoute(Routes.HEALTH))
        assertEquals(BottomNavTab.Generator, tabForRoute(Routes.GENERATOR))
        assertEquals(BottomNavTab.Settings, tabForRoute(Routes.SETTINGS))
    }

    @Test
    fun `las pantallas de cada pestana encienden la suya`() {
        listOf(Routes.VAULT_LOADING, Routes.VAULT_ITEM, Routes.VAULT_EDIT, Routes.CONFLICTS, Routes.CONFLICT_ITEM)
            .forEach { assertEquals(it, BottomNavTab.Vault, tabForRoute(it)) }
        listOf(Routes.HEALTH_RANKING, Routes.HEALTH_BREACHED, Routes.HEALTH_REPEATED, Routes.HEALTH_OLD)
            .forEach { assertEquals(it, BottomNavTab.Health, tabForRoute(it)) }
        listOf(
            Routes.TWO_FACTOR_SETUP, Routes.TWO_FACTOR_ACTIVE, Routes.CHANGE_PASSWORD, Routes.DELETE_ACCOUNT,
            Routes.CHANGE_EMAIL, Routes.DEVICES, Routes.ACTIVITY,
        ).forEach { assertEquals(it, BottomNavTab.Settings, tabForRoute(it)) }
    }

    @Test
    fun `el acceso, Desbloquear y Cuenta eliminada no llevan barra`() {
        listOf(
            Routes.ONBOARDING, Routes.LOGIN, Routes.SIGN_UP, Routes.TWO_FACTOR, Routes.BACKUP_CODE, Routes.UNLOCK,
            Routes.VERIFY_EMAIL, Routes.ACCOUNT_DELETED, Routes.LEGAL_DOCUMENT, Routes.LICENSES, Routes.ABOUT,
        ).forEach { assertNull(it, tabForRoute(it)) }
        assertNull(tabForRoute(null))
    }
}
