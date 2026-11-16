package com.soundchat.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soundchat.app.data.models.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM acoustic_messages ORDER BY timestampEpochMs ASC")
    fun observeAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query(
        """
        UPDATE acoustic_messages 
        SET deliveryState = :newState, retryCount = :retries, durationMs = :durationMs 
        WHERE id = :rowId
        """
    )
    suspend fun updateMessageStatus(
        rowId: Long,
        newState: String,
        retries: Int,
        durationMs: Long
    )

    @Query("SELECT * FROM acoustic_messages WHERE id = :rowId LIMIT 1")
    suspend fun getMessageById(rowId: Long): ChatMessageEntity?

    @Query("DELETE FROM acoustic_messages")
    suspend fun clearAllMessages()
}
