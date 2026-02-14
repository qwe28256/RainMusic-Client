package com.example.neumusic.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // 获取所有音乐（保持原有的按标题排序逻辑）
    @Query("SELECT * FROM music_table ORDER BY title ASC")
    fun getAllMusic(): Flow<List<MusicEntity>> // 返回 Flow，数据变动自动通知 UI

    // 批量插入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(music: List<MusicEntity>)

    // 清空所有数据
    @Query("DELETE FROM music_table")
    suspend fun clearAll()

    // 【新增】根据 ID 删除单首歌曲
    // 用于播放列表中的删除功能
    @Query("DELETE FROM music_table WHERE id = :id")
    suspend fun deleteMusicById(id: Long)
}
