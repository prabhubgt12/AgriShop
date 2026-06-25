package com.ledge.cashbook.ui

import com.ledge.cashbook.data.api.ProductInfo

object ScanState {
    var pendingProduct: ProductInfo? = null
        set(value) {
            field = value
        }
    
    fun consume(): ProductInfo? {
        val product = pendingProduct
        pendingProduct = null
        return product
    }
}
