package com.luis.end_security.feature.settings.ui.legal

/** El texto completo de una licencia, en `assets/licenses/`. [shortName] es el nombre corto que se ve en la lista. */
enum class LicenseText(val shortName: String, val assetPath: String) {
    Apache2("Apache 2.0", "licenses/apache-2.0.txt"),
    Mit("MIT", "licenses/mit.txt"),
    Ofl("OFL 1.1", "licenses/montserrat/OFL.txt"),
    CcBy4("CC BY 4.0", "licenses/cc-by-4.0.txt"),
}

/** Un componente de terceros: [name] y [holder] son nombres propios; [license] dice qué texto abre. */
data class LicenseEntry(val name: String, val holder: String, val license: LicenseText)

/**
 * La lista fija de lo que End Security usa (PEND-12). Se mantiene a mano junto con `gradle/libs.versions.toml`: si se agrega una
 * dependencia o un recurso con licencia, se agrega aquí.
 */
object Licenses {
    val entries: List<LicenseEntry> = listOf(
        LicenseEntry("AndroidX y Jetpack Compose", "Google", LicenseText.Apache2),
        LicenseEntry("Material Design 3", "Google", LicenseText.Apache2),
        LicenseEntry("Hilt y Dagger", "Google", LicenseText.Apache2),
        LicenseEntry("Kotlin, corrutinas y serialización", "JetBrains", LicenseText.Apache2),
        LicenseEntry("zxcvbn", "Nulab", LicenseText.Mit),
        LicenseEntry("Bouncy Castle", "The Legion of the Bouncy Castle", LicenseText.Mit),
        LicenseEntry("Montserrat (tipografía)", "Proyecto Montserrat", LicenseText.Ofl),
        LicenseEntry("Pwned Passwords", "Have I Been Pwned", LicenseText.CcBy4),
    )
}
