package com.durpalla.booking.ui.screens

import com.durpalla.booking.R
import com.durpalla.booking.core.AppCtx
import com.durpalla.booking.core.ApiUserMessage
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

/** Generic MVVM loader ViewModel: survives config changes and tab switches (state retention). */
class LoadVm<T>(private val loader: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state
    init { reload() }
    fun reload() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = runCatching { Load.Ok(loader()) }.getOrElse { Load.Err(it.message ?: AppCtx.str(R.string.generic_error)) }
        }
    }
}

@Composable
fun <T> rememberLoad(key: String, loader: suspend () -> T): LoadVm<T> = viewModel(key = key) { LoadVm(loader) }

fun todayIso(plus: Int = 0): String = java.time.LocalDate.now().plusDays(plus.toLong()).toString()
fun prettyDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM"))
}.getOrDefault(iso)
