package com.luis.end_security.core.crypto

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Un secreto (llave, `master_key`, `vault_key`…) fuera de las primitivas de `core/crypto`
 * (DEC-27). Dentro de las primitivas se usan `ByteArray`; en cuanto un secreto sale de ahí o se
 * guarda en un campo, va aquí.
 * - Guarda su propio arreglo: [copyOf] hace una copia defensiva.
 * - [use] presta los bytes sin sacarlos; no se debe guardar la referencia que recibe el bloque.
 * - [wipe] (o [close]) lo sobrescribe con ceros; leerlo después lanza [IllegalStateException],
 *   para que un secreto borrado antes de tiempo no se use como si fueran ceros válidos.
 * - [use] y [wipe] se excluyen: el autobloqueo no puede borrar la llave mientras otro hilo cifra
 *   con ella (cifraría con ceros sin error). Por eso el bloque de [use] debe ser corto: [wipe]
 *   lo espera.
 * - `toString()` no muestra nada y `equals`/`hashCode` son por identidad: dos secretos no se
 *   comparan por contenido sin querer (si hace falta, `MessageDigest.isEqual` dentro de [use]).
 *
 * Ojo: el `use` de esta clase solo presta los bytes; no lo cierra como `AutoCloseable.use` de
 * Kotlin (el miembro tiene prioridad). Para borrarlo al terminar: `try { … } finally { close() }`.
 */
class SecureBytes private constructor(private val bytes: ByteArray) : AutoCloseable {

    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var wiped = false

    val size: Int get() = bytes.size

    val isWiped: Boolean get() = wiped

    /** Presta los bytes a [block] sin copiarlos. Falla si ya se borró. */
    fun <R> use(block: (ByteArray) -> R): R = lock.read {
        check(!wiped) { "SecureBytes ya se borró" }
        block(bytes)
    }

    fun wipe() {
        lock.write {
            // Primero la bandera: nadie más lee a partir de aquí.
            wiped = true
            bytes.wipe()
        }
    }

    override fun close() = wipe()

    override fun toString(): String = "SecureBytes(***)"

    companion object {
        /** Copia [source]; quien llama sigue siendo dueño de [source] y debe borrarlo. */
        fun copyOf(source: ByteArray): SecureBytes = SecureBytes(source.copyOf())

        /**
         * Toma [owned] sin copiarlo: solo para arreglos recién creados dentro de `core/crypto`
         * que nadie más ve, así no queda una copia sin borrar.
         */
        internal fun adopt(owned: ByteArray): SecureBytes = SecureBytes(owned)
    }
}
