package com.ledge.cashbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledge.cashbook.data.local.entities.BusinessProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessProfileDao {
    @Query("SELECT * FROM business_profile WHERE id = 1")
    fun getProfileFlow(): Flow<BusinessProfile?>

    @Query("SELECT * FROM business_profile WHERE id = 1")
    suspend fun getProfile(): BusinessProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: BusinessProfile)

    @Update
    suspend fun update(profile: BusinessProfile)
}
