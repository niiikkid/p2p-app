package com.android.autopay.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.autopay.data.local.models.HistoryNotificationDBO
import com.android.autopay.data.local.models.UnsentNotificationDBO

@Database(entities = [UnsentNotificationDBO::class, HistoryNotificationDBO::class], version = 2)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun unsentNotificationDao(): UnsentNotificationDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        private const val DB_NAME = "database.db"
        private var INSTANCE: NotificationDatabase? = null
        private val LOCK = Any()

        fun getInstance(context: Context): NotificationDatabase {
            INSTANCE?.let { return it }

            synchronized(LOCK) {
                INSTANCE?.let { return it }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotificationDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                return instance
            }
        }
    }
}