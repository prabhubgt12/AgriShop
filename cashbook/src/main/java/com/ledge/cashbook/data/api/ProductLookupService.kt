package com.ledge.cashbook.data.api

import com.ledge.cashbook.data.local.dao.ProductBarcodeDao
import com.ledge.cashbook.data.local.entities.ProductBarcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ProductInfo(
    val name: String,
    val category: String = "",
    val brand: String = "",
    val price: Double? = null
)

object ProductLookupService {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun lookupByBarcode(
        barcode: String,
        localDao: ProductBarcodeDao? = null
    ): ProductInfo? = withContext(Dispatchers.IO) {
        // Check local database first
        if (localDao != null) {
            val localProduct = localDao.getByBarcodeSync(barcode)
            if (localProduct != null) {
                return@withContext ProductInfo(
                    name = localProduct.name,
                    category = localProduct.category,
                    brand = "",
                    price = localProduct.price
                )
            }
        }

        // Fallback to Open Food Facts API
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val jsonBody = json.parseToJsonElement(body) as? JsonObject ?: return@withContext null

            val product = jsonBody["product"] as? JsonObject ?: return@withContext null

            val name = product["product_name"]?.jsonPrimitive?.content
                ?: product["product_name_en"]?.jsonPrimitive?.content
                ?: product["generic_name"]?.jsonPrimitive?.content
                ?: ""

            val category = product["categories_tags"]?.jsonPrimitive?.content
                ?: product["categories"]?.jsonPrimitive?.content
                ?: ""

            val brand = product["brands"]?.jsonPrimitive?.content ?: ""

            if (name.isNotBlank()) {
                ProductInfo(name = name, category = category, brand = brand)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveToLocal(
        barcode: String,
        name: String,
        price: Double? = null,
        category: String = "",
        localDao: ProductBarcodeDao
    ) = withContext(Dispatchers.IO) {
        localDao.insert(
            ProductBarcode(
                barcode = barcode,
                name = name,
                price = price,
                category = category
            )
        )
    }
}
