package com.fertipos.agroshop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fertipos.agroshop.data.local.entities.CompanyProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyProfileDao {
    @Query("SELECT * FROM company_profile WHERE id = 1")
    fun getProfileFlow(): Flow<CompanyProfile?>

    @Query("SELECT * FROM company_profile WHERE id = 1")
    suspend fun getProfile(): CompanyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CompanyProfile)

    @Update
    suspend fun update(profile: CompanyProfile)
}
