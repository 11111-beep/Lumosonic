package com.example.lumosonic.room.recent

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_songs")
data class RecentSongEntity(
    @PrimaryKey
    val id: Long,
    val title: String?,
    val artist: String?,
    val data: String?,
    val duration: Long,
    val albumArtUri: String?,
    @ColumnInfo(name = "timestamp") // 添加时间戳字段
    val timestamp: Long // 添加一个时间戳字段来记录播放时间
)