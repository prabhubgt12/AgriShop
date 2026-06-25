package com.ledge.cashbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ledge.cashbook.data.local.entities.ProductBarcode
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductBarcodeDao {
    @Query("SELECT * FROM product_barcodes WHERE barcode = :barcode LIMIT 1")
    fun getByBarcode(barcode: String): Flow<ProductBarcode?>

    @Query("SELECT * FROM product_barcodes WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcodeSync(barcode: String): ProductBarcode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductBarcode)

    @Query("DELETE FROM product_barcodes WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}
