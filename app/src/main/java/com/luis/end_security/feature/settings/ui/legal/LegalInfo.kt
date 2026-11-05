package com.luis.end_security.feature.settings.ui.legal

/**
 * Datos de contacto y enlaces de «Acerca de». Un solo lugar para que el equipo los cambie.
 *
 * [PRIVACY_EMAIL] conserva el marcador de los documentos legales (`[privacidad@correo.com]`) hasta que el equipo ponga el correo real;
 * mientras tanto el botón «Reportar un problema de seguridad» abre el correo con ese texto como destinatario.
 */
object LegalInfo {
    const val PRIVACY_EMAIL = "[privacidad@correo.com]"

    /** Asunto del correo de «Reportar un problema de seguridad». */
    const val SECURITY_REPORT_SUBJECT = "Reporte de seguridad"

    const val REPOSITORY_URL = "https://github.com/MrX-zeta/End-Security"
}
