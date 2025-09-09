package com.example.lumosonic.room.playlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface PlaylistSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // 替换已存在
    fun insertPlaylistSong(song: PlaylistSongEntity)

    @Delete
    fun deletePlaylistSong(song: PlaylistSongEntity)

    @Query("SELECT * FROM playlist_songs ORDER BY title ASC") // 按标题升序获取所有播放列表
    suspend fun getAllPlaylistSongs(): List<PlaylistSongEntity>

    @Query("SELECT * FROM playlist_songs ORDER BY title ASC")
    fun getAllPlaylistSongsBlocking(): List<PlaylistSongEntity>
}