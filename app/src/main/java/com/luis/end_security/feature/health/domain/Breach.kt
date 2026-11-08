package com.luis.end_security.feature.health.domain

/** Resultado de preguntarle a Have I Been Pwned por una contraseña (OP-10, DEC-21). */
sealed interface BreachLookup {
    /** Apareció [count] veces en filtraciones (siempre > 0). */
    class Found(val count: Int) : BreachLookup {
        override fun toString(): String = "Found(***)"
    }

    /** No apareció. */
    data object Clean : BreachLookup

    /** Sin red o tiempo de espera agotado: lo no revisado queda «pendiente». */
    data object Offline : BreachLookup

    /** El servicio respondió algo que no sirve (limitación, error): se reintenta más tarde. */
    data object Unavailable : BreachLookup
}

/**
 * HIBP con k-anonimato (DEC-21): de la contraseña solo sale el prefijo de 5 caracteres de su SHA-1; el sufijo se
 * busca en memoria. Nunca se registra un prefijo, un sufijo ni una cuenta.
 */
interface BreachApi {
    suspend fun lookup(password: String): BreachLookup
}
