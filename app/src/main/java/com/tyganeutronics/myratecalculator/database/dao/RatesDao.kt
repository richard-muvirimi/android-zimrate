package com.tyganeutronics.myratecalculator.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tyganeutronics.myratecalculator.database.entities.RateEntity

@Dao
interface RatesDao {

    @Query("SELECT * FROM `rates` WHERE hidden = 0")
    fun getAll(): List<RateEntity>

    @Query("SELECT * FROM `rates` WHERE hidden = 0 AND pinned = 1 ORDER BY sort_order ASC")
    fun getAllPinned(): List<RateEntity>

    @Query("SELECT * FROM `rates` WHERE hidden = 0 ORDER BY sort_order ASC")
    fun getAllSorted(): LiveData<List<RateEntity>>

    @Query("SELECT * FROM `rates` WHERE hidden = 1 ORDER BY currency ASC")
    fun getAllHidden(): LiveData<List<RateEntity>>

    @Query("SELECT * FROM `rates` WHERE currency = :currency LIMIT 1")
    fun findByCurrency(currency: String): RateEntity?

    @Query("UPDATE `rates` SET hidden = :hidden WHERE currency = :currency")
    fun setHidden(currency: String, hidden: Boolean)

    @Query("UPDATE `rates` SET pinned = 1 WHERE currency = 'USD'")
    fun pinUsd()

    @Query("UPDATE `rates` SET sort_order = :sortOrder WHERE currency = :currency")
    fun setSortOrder(currency: String, sortOrder: Int)

    @Query("UPDATE `rates` SET pinned = :pinned WHERE currency = :currency")
    fun setPinned(currency: String, pinned: Boolean)

@Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rateEntity: RateEntity): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<RateEntity>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(rateEntity: RateEntity)

    @Delete
    fun delete(rateEntity: RateEntity)

    @Query("DELETE FROM `rates` WHERE currency = :currency")
    fun deleteByCurrency(currency: String)
}
