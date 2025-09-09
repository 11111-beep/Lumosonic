package com.example.lumosonic.room.recent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecentSong(song: RecentSongEntity)

    @Query("SELECT * FROM recent_songs ORDER BY timestamp DESC LIMIT 20") // 按时间降序获取最近播放,限制20个
    fun getRecentSongs(): List<RecentSongEntity>
}