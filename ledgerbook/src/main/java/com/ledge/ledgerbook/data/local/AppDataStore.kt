package com.ledge.ledgerbook.data.local

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// Single shared DataStore for the Ledgerbook module
val Context.ledgerDataStore by preferencesDataStore(name = "ledger_prefs")
