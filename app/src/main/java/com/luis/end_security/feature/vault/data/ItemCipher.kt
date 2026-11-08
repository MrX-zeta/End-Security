package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.crypto.AesGcm
import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.SecretReader
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.crypto.wipe
import com.luis.end_security.feature.vault.domain.VaultItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/** El bloque abrió (la etiqueta GCM es válida) pero el JSON no es un elemento v1. */
class ItemFormatException : Exception()

/**
 * Cifrado de un elemento (DEC-05, DEC-29): `AES-256-GCM(vault_key, json_elemento_v1, nonce, AAD)`
 * con AAD = `itemId|userId|formatVersion` (UUID canónicos, en ASCII). Así el servidor no puede
 * cambiar un bloque de elemento ni de usuario, ni hacerlo pasar por otra versión de formato.
 *
 * El JSON en bytes se borra después de cifrar o de leer. Quedan sin poder borrarse (DEC-27): las
 * `String` del modelo, la `String` con el JSON completo al cifrar y al leer, y los búferes de
 * caracteres que kotlinx.serialization reutiliza.
 *
 * Los campos desconocidos se ignoran al leer. Regla (DEC-29): un campo nuevo sube
 * `format_version`; si no, una app vieja que edite el elemento lo perdería sin aviso.
 */
@OptIn(ExperimentalSerializationApi::class)
object ItemCipher {
    const val FORMAT_VERSION = 1

    private val json = Json {
        // Compatibilidad futura: un campo nuevo de otra versión de la app no rompe la lectura.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encrypt(vaultKey: SecretReader, itemId: UUID, userId: UUID, item: VaultItem): Sealed {
        val plaintext = json.encodeToString(ItemJsonV1.serializer(), ItemJsonV1.from(item)).toByteArray(Charsets.UTF_8)
        try {
            return vaultKey.withBytes { AesGcm.seal(it, plaintext, aad(itemId, userId, FORMAT_VERSION)) }
        } finally {
            plaintext.wipe()
        }
    }

    /**
     * @throws AuthenticationFailed si el bloque no es de este elemento, de este usuario o de esta
     *   versión de formato, o si se alteró.
     * @throws ItemFormatException si abre pero no es un elemento v1 válido.
     */
    fun decrypt(vaultKey: SecretReader, itemId: UUID, userId: UUID, formatVersion: Int, sealed: Sealed): VaultItem {
        // La AAD lleva la versión que dice la fila: si alguien la cambia, la etiqueta no coincide.
        val plaintext = vaultKey.withBytes { AesGcm.open(it, sealed, aad(itemId, userId, formatVersion)) }
        try {
            if (formatVersion != FORMAT_VERSION) throw ItemFormatException()
            return json.decodeFromString(ItemJsonV1.serializer(), plaintext.toString(Charsets.UTF_8)).toItem()
        } catch (_: SerializationException) {
            throw ItemFormatException()
        } catch (_: IllegalArgumentException) {
            throw ItemFormatException()
        } catch (_: DateTimeParseException) {
            throw ItemFormatException()
        } finally {
            plaintext.wipe()
        }
    }

    /**
     * Para la lista: descifra y toma **solo** nombre, usuario y sitio. La contraseña y las notas no se
     * guardan ni se devuelven (el decodificador las salta; puede armar un texto temporal que no se retiene) y los
     * bytes se borran al terminar. Mismos errores que [decrypt].
     */
    fun decryptSummary(vaultKey: SecretReader, itemId: UUID, userId: UUID, formatVersion: Int, sealed: Sealed): ItemSummary {
        val plaintext = vaultKey.withBytes { AesGcm.open(it, sealed, aad(itemId, userId, formatVersion)) }
        try {
            if (formatVersion != FORMAT_VERSION) throw ItemFormatException()
            val summary = json.decodeFromStream(ItemSummaryJson.serializer(), ByteArrayInputStream(plaintext))
            return ItemSummary(summary.name, summary.username, summary.url)
        } catch (_: SerializationException) {
            throw ItemFormatException()
        } catch (_: IllegalArgumentException) {
            throw ItemFormatException()
        } finally {
            plaintext.wipe()
        }
    }

    private fun aad(itemId: UUID, userId: UUID, formatVersion: Int): ByteArray =
        "$itemId|$userId|$formatVersion".toByteArray(Charsets.US_ASCII)
}

/** Nombre, usuario y sitio de un elemento: lo único que la lista necesita. */
class ItemSummary(val name: String, val username: String, val url: String?) {
    override fun toString(): String = "ItemSummary(***)"
}

/** Solo los campos de la lista de `json_elemento_v1`; el resto (contraseña, notas, fechas) se ignora al leer. */
@Serializable
internal class ItemSummaryJson(
    @SerialName("name") val name: String,
    @SerialName("username") val username: String,
    @SerialName("url") val url: String? = null,
) {
    override fun toString(): String = "ItemSummaryJson(***)"
}

/** `json_elemento_v1` (DEC-29), con los nombres del grafo y fechas ISO-8601. */
@Serializable
internal class ItemJsonV1(
    @SerialName("name") val name: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("url") val url: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("password_changed_at") val passwordChangedAt: String,
) {
    fun toItem() = VaultItem(
        name = name,
        username = username,
        password = password,
        url = url,
        notes = notes,
        createdAt = Instant.parse(createdAt),
        passwordChangedAt = Instant.parse(passwordChangedAt),
    )

    override fun toString(): String = "ItemJsonV1(***)"

    companion object {
        fun from(item: VaultItem) = ItemJsonV1(
            name = item.name,
            username = item.username,
            password = item.password,
            url = item.url,
            notes = item.notes,
            createdAt = item.createdAt.toString(),
            passwordChangedAt = item.passwordChangedAt.toString(),
        )
    }
}
