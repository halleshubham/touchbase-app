package com.yourname.touchbase.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.yourname.touchbase.data.local.CallSessionDao
import com.yourname.touchbase.data.local.ContactDao
import com.yourname.touchbase.data.local.TouchBaseDatabase
import com.yourname.touchbase.data.local.EventDao
import com.yourname.touchbase.data.local.MessageTemplateDao
import com.yourname.touchbase.data.local.SavedFilterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TouchBaseDatabase =
        Room.databaseBuilder(
            context,
            TouchBaseDatabase::class.java,
            "touchbase.db"
        )
            // Pre-release, no installed base yet - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideContactDao(db: TouchBaseDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideMessageTemplateDao(db: TouchBaseDatabase): MessageTemplateDao = db.messageTemplateDao()

    @Provides
    fun provideEventDao(db: TouchBaseDatabase): EventDao = db.eventDao()

    @Provides
    fun provideCallSessionDao(db: TouchBaseDatabase): CallSessionDao = db.callSessionDao()

    @Provides
    fun provideSavedFilterDao(db: TouchBaseDatabase): SavedFilterDao = db.savedFilterDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
