package com.example.neumusic.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_table")
data class MusicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String,
    // 封面图路径 (本地缓存文件)
    val albumArtPath: String?,
    // 内嵌歌词 (Metadata)
    val embeddedLyrics: String?
)
