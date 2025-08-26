package com.fertipos.agroshop.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {

    val loggedIn = prefs.loggedInFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun logout() {
        viewModelScope.launch {
            prefs.setLoggedIn(false)
        }
    }
}
