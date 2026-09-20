package com.yourname.touchbase.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageTemplateDao {

    @Query("SELECT * FROM message_templates ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<MessageTemplate>>

    @Query("SELECT * FROM message_templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): MessageTemplate?

    @Upsert
    suspend fun upsert(template: MessageTemplate): Long

    @Delete
    suspend fun delete(template: MessageTemplate)

    // Only one template may be default at a time.
    @Query("UPDATE message_templates SET isDefault = 0")
    suspend fun clearDefaults()

    @Transaction
    suspend fun setAsDefault(templateId: Long) {
        clearDefaults()
        markDefault(templateId)
    }

    @Query("UPDATE message_templates SET isDefault = 1 WHERE id = :templateId")
    suspend fun markDefault(templateId: Long)
}
