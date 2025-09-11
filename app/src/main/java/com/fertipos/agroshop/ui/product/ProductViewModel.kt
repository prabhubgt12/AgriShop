package com.fertipos.agroshop.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.entities.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val dao: ProductDao
) : ViewModel() {

    data class UiState(
        val products: List<Product> = emptyList(),
        val error: String? = null
    )

    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<UiState> = combine(dao.getAll(), _error) { list, err ->
        UiState(products = list, error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun add(
        name: String,
        type: String,
        unit: String,
        sellingPrice: Double,
        purchasePrice: Double,
        stockQuantity: Double,
        gstPercent: Double
    ) {
        if (name.isBlank() || unit.isBlank()) return
        viewModelScope.launch {
            dao.insert(
                Product(
                    name = name.trim(),
                    type = type.trim(),
                    unit = unit.trim(),
                    sellingPrice = sellingPrice,
                    purchasePrice = purchasePrice,
                    stockQuantity = stockQuantity,
                    gstPercent = gstPercent
                )
            )
        }
    }

    fun update(
        product: Product,
        name: String,
        type: String,
        unit: String,
        sellingPrice: Double,
        purchasePrice: Double,
        stockQuantity: Double,
        gstPercent: Double
    ) {
        if (name.isBlank() || unit.isBlank()) return
        viewModelScope.launch {
            dao.update(
                product.copy(
                    name = name.trim(),
                    type = type.trim(),
                    unit = unit.trim(),
                    sellingPrice = sellingPrice,
                    purchasePrice = purchasePrice,
                    stockQuantity = stockQuantity,
                    gstPercent = gstPercent,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch {
            try {
                dao.delete(product)
            } catch (e: Exception) {
                _error.value = "ERR_PRODUCT_REFERENCED"
            }
        }
    }

    fun adjustStock(productId: Int, delta: Double) {
        viewModelScope.launch { dao.adjustStock(productId, delta) }
    }

    fun clearError() { _error.value = null }
}

