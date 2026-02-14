package com.example.neumusic.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_table ORDER BY title ASC")
    fun getAllMusic(): Flow<List<MusicEntity>> // 返回 Flow，数据变动自动通知 UI

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(music: List<MusicEntity>)

    @Query("DELETE FROM music_table")
    suspend fun clearAll()
}
