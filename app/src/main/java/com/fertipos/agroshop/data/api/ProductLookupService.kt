package com.fertipos.agroshop.data.api

import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.entities.Product
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
    val price: Double? = null,
    val productId: Int? = null
)

object ProductLookupService {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun lookupByBarcode(
        barcode: String,
        productDao: ProductDao? = null
    ): ProductInfo? = withContext(Dispatchers.IO) {
        // Check local database first
        if (productDao != null) {
            val localProduct = productDao.getByBarcodeSync(barcode)
            if (localProduct != null) {
                return@withContext ProductInfo(
                    name = localProduct.name,
                    category = localProduct.type,
                    brand = "",
                    price = localProduct.sellingPrice,
                    productId = localProduct.id
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
}
