package com.luis.end_security.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * INV-02: Room solo guarda bloques cifrados. Revisa el esquema real de la base (no el código):
 * ninguna columna con nombre de dato de un elemento en claro.
 */
@RunWith(AndroidJUnit4::class)
class SchemaPlaintextTest {

    private val forbidden = setOf("name", "username", "password", "url", "notes", "title", "website", "login")

    @Test
    fun ninguna_columna_guarda_texto_claro_de_un_elemento() {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        try {
            val tables = database.query("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }.filterNot { it.startsWith("sqlite_") || it.startsWith("android_") || it == "room_master_table" }
            assertEquals(setOf("account", "vault_items", "breach_checks", "health_snapshots"), tables.toSet())

            tables.forEach { table ->
                val columns = database.query("PRAGMA table_info($table)", null).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
                }
                val leaked = columns.filter { it.lowercase() in forbidden }
                assertTrue("$table tiene columnas en claro: $leaked", leaked.isEmpty())
            }

            // Los elementos solo tienen el bloque cifrado y metadatos de sincronización.
            val itemColumns = database.query("PRAGMA table_info(vault_items)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
            }
            assertEquals(
                setOf(
                    "id", "format_version", "nonce", "ciphertext", "server_version", "server_revision",
                    "local_seq", "sync_state", "deleted_at", "updated_at",
                ),
                itemColumns,
            )
        } finally {
            database.close()
        }
    }
}
