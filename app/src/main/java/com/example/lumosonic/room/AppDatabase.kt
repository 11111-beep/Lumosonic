package com.example.lumosonic.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lumosonic.room.favorite.FavoriteSongDao
import com.example.lumosonic.room.favorite.FavoriteSongEntity
import com.example.lumosonic.room.playlist.PlaylistSongDao
import com.example.lumosonic.room.playlist.PlaylistSongEntity
import com.example.lumosonic.room.recent.RecentSongDao
import com.example.lumosonic.room.recent.RecentSongEntity

@Database(
    entities = [FavoriteSongEntity::class, RecentSongEntity::class, PlaylistSongEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteSongDao(): FavoriteSongDao
    abstract fun recentSongDao(): RecentSongDao
    abstract fun playlistSongDao(): PlaylistSongDao

    // 创建数据库实例
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "song_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}