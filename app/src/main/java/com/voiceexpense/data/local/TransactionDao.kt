package com.voiceexpense.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.voiceexpense.data.model.SheetReference
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Transaction?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    // Observability
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY createdAt ASC")
    fun observeByStatus(status: TransactionStatus): Flow<List<Transaction>>

    // Queue helpers
    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: TransactionStatus): List<Transaction>

    @Query("UPDATE transactions SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: String, newStatus: TransactionStatus): Int

    @Query("UPDATE transactions SET sheetRef = :ref, status = :newStatus WHERE id = :id")
    suspend fun setPosted(id: String, ref: SheetReference, newStatus: TransactionStatus): Int
}

