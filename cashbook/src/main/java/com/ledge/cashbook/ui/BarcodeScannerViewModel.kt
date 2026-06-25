package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.dao.ProductBarcodeDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    private val productBarcodeDao: ProductBarcodeDao
) : ViewModel() {
    
    val dao: ProductBarcodeDao = productBarcodeDao
}
