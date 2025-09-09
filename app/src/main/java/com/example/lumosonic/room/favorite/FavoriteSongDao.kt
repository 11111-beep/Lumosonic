package com.example.lumosonic.room.favorite

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // 替换已存在的收藏
    fun insertFavoriteSong(song: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE id = :songId")
    fun deleteFavoriteSongById(songId: Long) // 删除指定 ID 的收藏

    @Query("SELECT * FROM favorite_songs ORDER BY id DESC")
    fun getAllFavoriteSongsBlocking(): List<FavoriteSongEntity> // 获取所有收藏歌曲


    @Query("SELECT COUNT(*) FROM favorite_songs WHERE id = :songId")
    fun countFavorite(songId: Long): Int // 使用 COUNT 查询是否存在，更高效
}