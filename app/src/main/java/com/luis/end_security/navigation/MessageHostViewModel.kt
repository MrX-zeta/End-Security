package com.luis.end_security.navigation

import androidx.lifecycle.ViewModel
import com.luis.end_security.core.common.AppMessage
import com.luis.end_security.core.common.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

/** Entrega al anfitrión de avisos los que piden los ViewModels (ver [AppMessages]). */
@HiltViewModel
class MessageHostViewModel @Inject constructor(appMessages: AppMessages) : ViewModel() {
    val messages: SharedFlow<AppMessage> = appMessages.messages
}
