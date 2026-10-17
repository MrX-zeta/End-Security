package com.luis.end_security.core.common

import javax.inject.Qualifier

/** Dispatcher para trabajo de CPU (p. ej. estimar la fuerza de una contraseña). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
