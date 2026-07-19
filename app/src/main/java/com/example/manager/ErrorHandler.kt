package com.example.manager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ErrorHandler {
    private val _errors = MutableSharedFlow<String>(replay = 0)
    val errors = _errors.asSharedFlow()

    suspend fun reportError(message: String) {
        _errors.emit(message)
    }
}
