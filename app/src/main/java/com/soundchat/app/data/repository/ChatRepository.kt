package com.soundchat.app.data.repository

import com.soundchat.app.data.database.ChatMessageDao
import com.soundchat.app.data.models.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatMessageDao) {

    val allMessages: Flow<List<ChatMessageEntity>> = dao.observeAllMessages()

    suspend fun insertMessage(message: ChatMessageEntity): Long {
        return dao.insertMessage(message)
    }

    suspend fun updateMessageStatus(
        rowId: Long,
        newState: String,
        retries: Int,
        durationMs: Long
    ) {
        dao.updateMessageStatus(rowId, newState, retries, durationMs)
    }

    suspend fun getMessageById(rowId: Long): ChatMessageEntity? {
        return dao.getMessageById(rowId)
    }

    suspend fun clearAllHistory() {
        dao.clearAllMessages()
    }
}
