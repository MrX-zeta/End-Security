package com.luis.end_security.feature.vault.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Búsqueda y orden de la lista, locales y en memoria (OP-07). El texto buscado nunca se guarda.
 * Sin acentos ni mayúsculas: «gmáil» encuentra «Gmail».
 */
object ItemSearch {
    private val diacritics = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        diacritics.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    /** Por nombre, usuario y sitio. Una consulta vacía muestra todo. */
    fun matches(item: VaultItem, query: String): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        return normalize(item.name).contains(needle) ||
            normalize(item.username).contains(needle) ||
            item.url?.let { normalize(it).contains(needle) } == true
    }

    /** Alfabético por nombre (sin acentos ni mayúsculas); los dañados o de otra versión, al final. */
    fun sorted(entries: List<VaultEntry>): List<VaultEntry> {
        val (readable, others) = entries.partition { it is ReadableEntry }
        return readable.sortedBy { normalize((it as ReadableEntry).item.name) } + others.sortedBy { it.id }
    }
}
