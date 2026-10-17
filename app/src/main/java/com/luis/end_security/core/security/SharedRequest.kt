package com.luis.end_security.core.security

import androidx.annotation.MainThread
import java.util.WeakHashMap

/**
 * Petición compartida sobre un objeto (una ventana o una vista) que varias pantallas pueden
 * pedir a la vez. Durante una transición de navegación conviven la pantalla que sale y la que
 * entra: si cada una guardara y restaurara el valor por su cuenta, la que sale deshace lo que
 * pidió la que entra. Aquí el primer [acquire] aplica el cambio y guarda el valor original, y
 * solo el último [release] lo restaura.
 *
 * Las llaves se guardan con referencia débil. Solo se usa desde el hilo principal.
 */
internal class SharedRequest<K : Any, S>(
    private val apply: (K) -> S,
    private val restore: (K, S) -> Unit,
) {
    private class Entry<S>(var count: Int, val original: S)

    private val entries = WeakHashMap<K, Entry<S>>()

    @MainThread
    fun acquire(key: K) {
        val entry = entries[key]
        if (entry == null) {
            entries[key] = Entry(count = 1, original = apply(key))
        } else {
            entry.count++
        }
    }

    @MainThread
    fun release(key: K) {
        val entry = entries[key] ?: return
        entry.count--
        if (entry.count == 0) {
            entries.remove(key)
            restore(key, entry.original)
        }
    }

    /** Cuántas pantallas tienen hoy la petición sobre [key]. */
    @MainThread
    fun count(key: K): Int = entries[key]?.count ?: 0
}
