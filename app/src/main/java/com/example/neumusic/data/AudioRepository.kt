package com.example.neumusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import com.example.neumusic.data.database.MusicDatabase
import com.example.neumusic.data.database.MusicEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.UUID

// UI 层使用的模型
data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val path: String,
    val embeddedLyrics: String?
)

class AudioRepository(private val context: Context) {
    private val musicDao = MusicDatabase.getDatabase(context).musicDao()
    private val TAG = "DUU"

    val allMusic: Flow<List<AudioFile>> = musicDao.getAllMusic().map { entities ->
        entities.map { entity ->
            AudioFile(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                duration = entity.duration,
                uri = Uri.parse(entity.path),
                // 使用 toUri() 扩展函数更安全
                albumArtUri = entity.albumArtPath?.let { File(it).toUri() },
                path = entity.path,
                embeddedLyrics = entity.embeddedLyrics
            )
        }
    }

    fun scanUriAndSave(treeUriStr: String): Flow<String> = flow {
        val startTime = System.currentTimeMillis()
        emit("正在分析目录...")

        val treeUri = Uri.parse(treeUriStr)
        val folderPath = getAbsolutePathFromSafUri(treeUri)

        if (folderPath == null || !File(folderPath).exists()) {
            emit("错误：目录不存在")
            return@flow
        }

        val rootFile = File(folderPath)
        val coversDir = File(context.cacheDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val existingPaths = try {
            musicDao.getAllMusic().first().map { it.path }.toMutableSet()
        } catch (e: Exception) {
            mutableSetOf<String>()
        }

        Log.d(TAG, "数据库现有: ${existingPaths.size}")

        val newEntities = mutableListOf<MusicEntity>()
        var addedCount = 0

        val mmr = MediaMetadataRetriever()

        rootFile.walkTopDown()
            .filter { file ->
                file.isFile && (
                        file.extension.equals("mp3", true) ||
                                file.extension.equals("flac", true) ||
                                file.extension.equals("m4a", true) ||
                                file.extension.equals("wav", true)
                        )
            }
            .forEach { file ->
                if (existingPaths.contains(file.absolutePath)) {
                    return@forEach
                }

                try {
                    mmr.setDataSource(file.absolutePath)
                    val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                    val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val duration = durationStr?.toLongOrNull() ?: 0L

                    var coverPath: String? = null
                    try {
                        val embeddedPic = mmr.embeddedPicture
                        if (embeddedPic != null) {
                            val coverFile = File(coversDir, "${UUID.randomUUID()}.jpg")
                            FileOutputStream(coverFile).use { it.write(embeddedPic) }
                            coverPath = coverFile.absolutePath
                        }
                    } catch (e: Exception) {}

                    // 【修复】全部使用具名参数，确保类型对应
                    newEntities.add(MusicEntity(
                        title = title,
                        artist = artist,
                        duration = duration, // 这里是 Long
                        path = file.absolutePath, // 这里是 String
                        albumArtPath = coverPath,
                        embeddedLyrics = null
                    ))
                    addedCount++

                    if (newEntities.size >= 50) {
                        musicDao.insertAll(newEntities)
                        newEntities.clear()
                    }
                } catch (e: Exception) {
                    // 【修复】catch 块里也要用具名参数，防止把 name(String) 传给 duration(Long)
                    newEntities.add(MusicEntity(
                        title = file.name,
                        artist = "Unknown",
                        duration = 0L, // 显式传 0L
                        path = file.absolutePath,
                        albumArtPath = null,
                        embeddedLyrics = null
                    ))
                    addedCount++
                }
            }

        try { mmr.release() } catch (e: Exception) {}

        if (newEntities.isNotEmpty()) musicDao.insertAll(newEntities)

        emit("正在清理无效条目...")
        var deletedCount = 0
        var cleanedCovers = 0

        val allMusicList = try {
            musicDao.getAllMusic().first()
        } catch (e: Exception) {
            emptyList()
        }

        val toDeleteIds = mutableListOf<Long>()
        val activeCoverPaths = mutableSetOf<String>()

        for (music in allMusicList) {
            if (!music.albumArtPath.isNullOrEmpty()) {
                activeCoverPaths.add(music.albumArtPath)
            }

            if (music.path.startsWith(folderPath)) {
                if (!File(music.path).exists()) {
                    toDeleteIds.add(music.id)
                    // 如果要删除了，就从活跃封面列表中移除
                    if (!music.albumArtPath.isNullOrEmpty()) {
                        activeCoverPaths.remove(music.albumArtPath)
                    }
                }
            }
        }

        toDeleteIds.forEach {
            musicDao.deleteMusicById(it)
            deletedCount++
        }

        // 清理孤儿封面
        if (coversDir.exists() && coversDir.isDirectory) {
            coversDir.listFiles()?.forEach { coverFile ->
                if (coverFile.isFile && !activeCoverPaths.contains(coverFile.absolutePath)) {
                    if (coverFile.delete()) {
                        cleanedCovers++
                    }
                }
            }
        }

        val timeUsed = (System.currentTimeMillis() - startTime) / 1000f
        val msg = "完成! 耗时${String.format("%.1f", timeUsed)}s | 新增: $addedCount | 移除: $deletedCount | 清理图片: $cleanedCovers"
        Log.i(TAG, msg)
        emit(msg)

    }.flowOn(Dispatchers.IO)

    private fun getAbsolutePathFromSafUri(uri: Uri): String? {
        try {
            val path = uri.path ?: return null
            val id = if (path.contains("/document/")) path.substringAfter("/document/") else path.substringAfter("/tree/")
            val decodedId = URLDecoder.decode(id, "UTF-8")

            if (decodedId.contains(":")) {
                val split = decodedId.split(":")
                val type = split[0]
                val realPath = if (split.size > 1) split[1] else ""

                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().absolutePath + "/" + realPath
                } else {
                    return "/storage/$type/$realPath"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
