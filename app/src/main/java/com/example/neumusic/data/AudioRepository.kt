package com.example.neumusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment // 导入 Environment
import androidx.documentfile.provider.DocumentFile
import com.example.neumusic.data.database.MusicDatabase
import com.example.neumusic.data.database.MusicEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder // 导入 URLDecoder
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

    val allMusic: Flow<List<AudioFile>> = musicDao.getAllMusic().map { entities ->
        entities.map { entity ->
            AudioFile(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                duration = entity.duration,
                uri = Uri.parse(entity.path),
                albumArtUri = entity.albumArtPath?.let { Uri.fromFile(File(it)) },
                path = entity.path,
                embeddedLyrics = entity.embeddedLyrics
            )
        }
    }

    fun scanUriAndSave(treeUriStr: String): Flow<String> = flow {
        emit("正在初始化扫描...")

        val treeUri = Uri.parse(treeUriStr)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)

        if (rootDoc == null || !rootDoc.exists()) {
            emit("错误：无法访问该目录")
            return@flow
        }

        val entities = mutableListOf<MusicEntity>()
        val coversDir = File(context.cacheDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val mmr = MediaMetadataRetriever()
        var scannedCount = 0

        suspend fun traverse(doc: DocumentFile) {
            if (doc.isDirectory) {
                doc.listFiles().forEach { traverse(it) }
            } else if (doc.isFile) {
                val name = doc.name ?: ""
                if (name.endsWith(".mp3", true) ||
                    name.endsWith(".flac", true) ||
                    name.endsWith(".m4a", true)) {

                    emit("发现: $name")

                    try {
                        mmr.setDataSource(context, doc.uri)

                        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name.substringBeforeLast(".")
                        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                        val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                        var coverPath: String? = null
                        val embeddedPic = mmr.embeddedPicture
                        if (embeddedPic != null) {
                            val coverFile = File(coversDir, "${UUID.randomUUID()}.jpg")
                            FileOutputStream(coverFile).use { it.write(embeddedPic) }
                            coverPath = coverFile.absolutePath
                        }

                        // 【关键修复】尝试获取真实路径
                        // 如果这个 doc 是 file:// 类型的 Uri，直接 toString 可能带前缀，最好用 path
                        // 如果是 content:// 类型的 Uri (SAF)，尝试转换
                        val realPath = getAbsolutePathFromSafUri(doc.uri) ?: doc.uri.path ?: doc.uri.toString()

                        entities.add(MusicEntity(
                            title = title,
                            artist = artist,
                            duration = duration,
                            path = realPath, // 存入真实路径，让 LyricsHelper 能找到它！
                            albumArtPath = coverPath,
                            embeddedLyrics = null
                        ))
                        scannedCount++

                    } catch (e: Exception) {
                        val realPath = getAbsolutePathFromSafUri(doc.uri) ?: doc.uri.toString()
                        entities.add(MusicEntity(
                            title = name,
                            artist = "Unknown",
                            duration = 0,
                            path = realPath,
                            albumArtPath = null,
                            embeddedLyrics = null
                        ))
                        scannedCount++
                    }
                }
            }
        }

        traverse(rootDoc)

        try { mmr.release() } catch (e: Exception) {}

        emit("正在写入数据库...")
        if (entities.isNotEmpty()) {
            musicDao.clearAll()
            musicDao.insertAll(entities)
        }

        emit("扫描完成，共找到 $scannedCount 首歌曲")

    }.flowOn(Dispatchers.IO)

    /**
     * 【辅助工具】将 SAF URI 尝试解析为绝对路径
     * 这个函数是私有的 (private)，只能在这个类里面用
     */
    private fun getAbsolutePathFromSafUri(uri: Uri): String? {
        try {
            // DocumentFile 的 URI 通常格式为: content://.../tree/primary%3AMusic/document/primary%3AMusic%2FSong.mp3
            val path = uri.path ?: return null

            // 关键逻辑：解析 SAF 编码的路径部分
            // 常见的 SAF URI 最后一段包含 "primary:..." 或 "1234-5678:..."
            val id = if (path.contains("/document/")) {
                path.substringAfter("/document/")
            } else {
                path
            }

            // 解码 URL 编码 (例如 %3A -> :)
            val decodedId = URLDecoder.decode(id, "UTF-8")

            if (decodedId.contains(":")) {
                val split = decodedId.split(":")
                val type = split[0]
                val realPath = if (split.size > 1) split[1] else ""

                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().absolutePath + "/" + realPath
                } else {
                    // 外置 SD 卡路径，通常是 /storage/UUID/...
                    // 这里做一个简单的假设，具体路径可能因厂商而异
                    return "/storage/$type/$realPath"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

} // <--- 类结束
