package com.fertipos.agroshop.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.entities.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

    val state: StateFlow<UiState> = dao.getAll()
        .map { UiState(products = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun add(
        name: String,
        type: String,
        unit: String,
        pricePerUnit: Double,
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
                    pricePerUnit = pricePerUnit,
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
        pricePerUnit: Double,
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
                    pricePerUnit = pricePerUnit,
                    stockQuantity = stockQuantity,
                    gstPercent = gstPercent,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch { dao.delete(product) }
    }

    fun adjustStock(productId: Int, delta: Double) {
        viewModelScope.launch { dao.adjustStock(productId, delta) }
    }
}
