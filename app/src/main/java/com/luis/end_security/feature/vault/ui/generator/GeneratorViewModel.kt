package com.luis.end_security.feature.vault.ui.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.crypto.GeneratorOptions
import com.luis.end_security.core.security.SecretClipboard
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.vault.domain.GeneratorRepository
import com.luis.end_security.feature.vault.domain.PasswordHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.CharBuffer
import javax.inject.Inject

/** Las clases que se pueden apagar (las minúsculas siempre van, DEC-24). */
enum class GeneratorToggle { Uppercase, Digits, Symbols, AvoidAmbiguous }

/**
 * Estado del Generador (SCR-15). La contraseña generada se muestra siempre (es lo que la persona viene a ver), así que
 * va aquí como `String` (DEC-27); se borra al salir de primer plano y al bloquear, y **nunca** va a `SavedStateHandle`
 * ni a disco. [generation] sube con cada contraseña nueva, para la animación de opacidad.
 */
class GeneratorUiState(
    val options: GeneratorOptions = GeneratorOptions(),
    val password: String = "",
    /** 0–4 del medidor; `null` mientras no hay contraseña. */
    val strengthScore: Int? = null,
    val generation: Int = 0,
    /** Segundos tras los que se borra lo copiado, mientras dura el aviso «Copiada». */
    val copiedSeconds: Int? = null,
) {
    override fun toString(): String = "GeneratorUiState(options=$options, generation=$generation, ***)"
}

sealed interface GeneratorEffect {
    /** «Guardar en la bóveda»: a Nueva contraseña, con la generada ya en [PasswordHandoff]. */
    data object OpenNewWithPassword : GeneratorEffect

    /** «Usar esta contraseña» (en la hoja de Nueva/editar): la devuelve al campo. No imprime la contraseña. */
    class Use(val password: String) : GeneratorEffect {
        override fun toString(): String = "Use(***)"
    }
}

/**
 * Generador de contraseñas (OP-08, DEC-24). Las opciones se recuerdan en DataStore; la contraseña se vuelve a generar
 * cada vez que se abre y nunca se guarda en disco. La copia canónica es un `CharArray` que se sobrescribe al reemplazarla.
 */
@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val repository: GeneratorRepository,
    private val estimator: PasswordStrengthEstimator,
    private val clipboard: SecretClipboard,
    private val handoff: PasswordHandoff,
    private val session: VaultSession,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    private val _effects = Channel<GeneratorEffect>(Channel.BUFFERED)
    val effects: Flow<GeneratorEffect> = _effects.receiveAsFlow()

    private var current: CharArray? = null
    private var optionsLoaded = false
    private var copiedJob: Job? = null
    private var strengthJob: Job? = null

    init {
        ensureGenerated()
        viewModelScope.launch {
            session.state.collect { state -> if (state is VaultState.Locked) onHide() }
        }
    }

    /** Al abrir, o al volver a primer plano con la contraseña ya borrada: genera una nueva con las opciones recordadas. */
    fun ensureGenerated() {
        if (current != null) return
        viewModelScope.launch {
            if (!optionsLoaded) {
                val saved = repository.options.first()
                optionsLoaded = true
                _state.update { it.withOptions(saved) }
            }
            if (current == null && session.state.value is VaultState.Unlocked) regenerate()
        }
    }

    /** El dado: otra contraseña con las mismas opciones. */
    fun onRegenerate() = regenerate()

    /** El deslizador se mueve: se genera otra de ese largo; se guarda al soltar ([onLengthChangeFinished]). */
    fun onLengthChange(length: Int) {
        _state.update { it.withOptions(it.options.copy(length = length).sanitized()) }
        regenerate()
    }

    fun onLengthChangeFinished() = saveOptions()

    fun onToggle(toggle: GeneratorToggle) {
        val options = _state.value.options
        val updated = when (toggle) {
            GeneratorToggle.Uppercase -> options.copy(uppercase = !options.uppercase)
            GeneratorToggle.Digits -> options.copy(digits = !options.digits)
            GeneratorToggle.Symbols -> options.copy(symbols = !options.symbols)
            GeneratorToggle.AvoidAmbiguous -> options.copy(avoidAmbiguous = !options.avoidAmbiguous)
        }
        _state.update { it.withOptions(updated) }
        saveOptions()
        regenerate()
    }

    /** Copiar (DEC-25): sensible y se borra solo; el aviso dura 2,5 s. */
    fun onCopy() {
        val text = current?.let { String(it) } ?: return
        copiedJob?.cancel()
        copiedJob = viewModelScope.launch {
            val seconds = clipboard.copy(text)
            _state.update { it.withCopied(seconds) }
            delay(COPIED_NOTICE_MILLIS)
            _state.update { it.withCopied(null) }
        }
    }

    /** «Guardar en la bóveda»: entrega la contraseña una sola vez a Nueva contraseña, en memoria. */
    fun onSave() {
        val password = current ?: return
        handoff.put(password)
        viewModelScope.launch { _effects.send(GeneratorEffect.OpenNewWithPassword) }
    }

    /** «Usar esta contraseña» en la hoja de Nueva/editar. */
    fun onUse() {
        val password = current?.let { String(it) } ?: return
        viewModelScope.launch { _effects.send(GeneratorEffect.Use(password)) }
    }

    /** La app salió de primer plano, la hoja se cerró o se bloqueó la bóveda: se borra lo generado. */
    fun onHide() {
        strengthJob?.cancel()
        copiedJob?.cancel()
        current?.fill('\u0000')
        current = null
        _state.update { GeneratorUiState(options = it.options, generation = it.generation) }
    }

    private fun regenerate() {
        if (session.state.value !is VaultState.Unlocked) return
        val options = _state.value.options
        current?.fill('\u0000')
        val fresh = repository.generate(options)
        current = fresh
        _state.update { it.withPassword(String(fresh), generation = it.generation + 1) }
        scoreStrength(fresh.copyOf())
    }

    private fun scoreStrength(snapshot: CharArray) {
        strengthJob?.cancel()
        strengthJob = viewModelScope.launch {
            try {
                val score = withContext(defaultDispatcher) { estimator.score(CharBuffer.wrap(snapshot)) }
                _state.update { it.withStrength(score) }
            } finally {
                snapshot.fill('\u0000')
            }
        }
    }

    private fun saveOptions() {
        val options = _state.value.options
        viewModelScope.launch { repository.saveOptions(options) }
    }

    override fun onCleared() {
        current?.fill('\u0000')
        current = null
    }

    private fun GeneratorUiState.withOptions(options: GeneratorOptions) =
        GeneratorUiState(options, password, strengthScore, generation, copiedSeconds)

    private fun GeneratorUiState.withPassword(password: String, generation: Int) =
        GeneratorUiState(options, password, strengthScore, generation, null)

    private fun GeneratorUiState.withStrength(score: Int?) =
        GeneratorUiState(options, password, score, generation, copiedSeconds)

    private fun GeneratorUiState.withCopied(seconds: Int?) =
        GeneratorUiState(options, password, strengthScore, generation, seconds)

    companion object {
        /** Igual que el aviso «Copiada» del detalle (SCR-13): 2,5 s. */
        const val COPIED_NOTICE_MILLIS = 2_500L
    }
}
