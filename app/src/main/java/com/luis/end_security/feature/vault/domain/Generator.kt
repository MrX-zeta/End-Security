package com.luis.end_security.feature.vault.domain

import com.luis.end_security.core.crypto.GeneratorOptions
import kotlinx.coroutines.flow.Flow

/**
 * Generador de contraseñas (OP-08, DEC-24). Las **opciones** se recuerdan; la **contraseña generada** nunca se guarda
 * en disco: se vuelve a generar al abrir.
 */
interface GeneratorRepository {
    /** Las opciones recordadas (DataStore, `generator_*`), con valores por omisión la primera vez. */
    val options: Flow<GeneratorOptions>

    suspend fun saveOptions(options: GeneratorOptions)

    /** Una contraseña nueva con [options]. Quien la recibe la sobrescribe al terminar. */
    fun generate(options: GeneratorOptions): CharArray
}

/**
 * Entrega de una sola vez de una contraseña generada al formulario de Nueva contraseña («Guardar en la bóveda»).
 * Va en memoria: **no** pasa por argumentos de navegación, `SavedStateHandle` ni disco. [take] la devuelve y la
 * borra de aquí; con la bóveda bloqueada se vacía sola.
 */
interface PasswordHandoff {
    /** Guarda una copia de [password] (el original sigue siendo de quien llama). */
    fun put(password: CharArray)

    /** Devuelve la contraseña entregada y la borra; `null` si no hay (o ya se tomó). */
    fun take(): CharArray?

    fun clear()
}
