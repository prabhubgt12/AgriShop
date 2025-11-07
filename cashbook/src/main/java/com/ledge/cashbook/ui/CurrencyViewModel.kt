package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.prefs.CurrencyPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CurrencyViewModel @Inject constructor(
    private val prefs: CurrencyPreferences
) : ViewModel() {
    val currencyCode: StateFlow<String> = prefs.currencyCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "INR")
    val showSymbol: StateFlow<Boolean> = prefs.showSymbol
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setCurrencyCode(code: String) {
        viewModelScope.launch { prefs.setCurrencyCode(code) }
    }

    fun setShowSymbol(show: Boolean) {
        viewModelScope.launch { prefs.setShowSymbol(show) }
    }
}
