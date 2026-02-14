package com.example.neumusic.utils

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern

data class LyricsLine(
    val startTime: Long, // 毫秒
    val text: String
) : Comparable<LyricsLine> {
    override fun compareTo(other: LyricsLine): Int {
        return (this.startTime - other.startTime).toInt()
    }
}

object LyricsHelper {

    init {
        // 关闭 JAudioTagger 的繁琐日志
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    // 1. 匹配时间戳: [mm:ss.xx] 或 [mm:ss:xx] 或 [mm:ss]
    private val TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})([.:](\\d{1,3}))?\\]")

    // 2. 匹配元数据标签 (如 [ti:Title]) - 用于过滤
    private val TAG_PATTERN = Pattern.compile("\\[(ti|ar|al|by|offset):.*?\\]")

    /**
     * 获取歌词的入口方法
     * 策略：内嵌歌词 (Metadata) > 同名文件 (.lrc)
     */
    fun getLyrics(audioPath: String): List<LyricsLine> {
        // 1. 尝试读取内嵌歌词
        var rawLyrics = getEmbeddedLyrics(audioPath)

        // 2. 如果内嵌歌词为空，尝试读取同名 lrc 文件
        if (rawLyrics.isNullOrEmpty()) {
            rawLyrics = getLrcFileContent(audioPath)
        }

        // 3. 如果还是没有，返回空列表 (UI层会显示 No Lyrics)
        if (rawLyrics.isNullOrEmpty()) {
            return emptyList()
        }

        // 4. 解析歌词内容
        return parseLrc(rawLyrics)
    }

    /**
     * 使用 JAudioTagger 读取内嵌歌词
     */
    private fun getEmbeddedLyrics(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null

            // JAudioTagger 读取音频文件
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null

            // 尝试读取歌词字段 (兼容 ID3v2, Vorbis 等)
            val lyrics = tag.getFirst(FieldKey.LYRICS)

            // 有些文件可能存的是 CUSTOM 标签，但通常 LYRICS 足够覆盖大多数情况
            if (lyrics.isNotBlank()) lyrics else null
        } catch (e: Exception) {
            // 读取失败（可能是文件损坏或权限问题），静默失败，交给 fallback 处理
            e.printStackTrace()
            null
        }
    }

    /**
     * 读取同名 .lrc 文件
     */
    private fun getLrcFileContent(path: String): String? {
        return try {
            // 移除后缀 (如 .mp3) 并加上 .lrc
            val lrcPath = path.substringBeforeLast(".") + ".lrc"
            val lrcFile = File(lrcPath)

            if (lrcFile.exists() && lrcFile.canRead()) {
                lrcFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 统一解析逻辑 (正则 + 清洗)
     */
    private fun parseLrc(lrcContent: String): List<LyricsLine> {
        val result = ArrayList<LyricsLine>()

        // 统一换行符
        val lines = lrcContent.replace("\r\n", "\n").replace("\r", "\n").split("\n")

        for (line in lines) {
            val trimLine = line.trim()

            if (trimLine.isEmpty()) continue
            if (TAG_PATTERN.matcher(trimLine).matches()) continue

            // 提取时间戳
            val matcher = TIME_PATTERN.matcher(trimLine)
            val timeStamps = ArrayList<Long>()

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0
                val sec = matcher.group(2)?.toLongOrNull() ?: 0
                val msStr = matcher.group(4)

                val ms = when {
                    msStr == null -> 0L
                    msStr.length == 1 -> msStr.toLong() * 100
                    msStr.length == 2 -> msStr.toLong() * 10
                    else -> msStr.take(3).toLong()
                }

                val time = (min * 60 * 1000) + (sec * 1000) + ms
                timeStamps.add(time)
            }

            // 如果没有时间戳，说明这行可能是纯文本歌词（有些内嵌歌词是不带时间轴的纯文本）
            // 如果要支持纯文本滚动，逻辑会很复杂。这里为了保证同步体验，暂且只支持带时间戳的行。
            // 如果您希望显示纯文本，需要另外一套 UI 逻辑。
            if (timeStamps.isEmpty()) continue

            // 提取并清洗文本
            var content = matcher.replaceAll("").trim()

            // 移除 // 注释
            if (content.contains("//")) {
                content = content.substringBefore("//").trim()
            }

            if (content.isEmpty()) continue

            for (time in timeStamps) {
                result.add(LyricsLine(time, content))
            }
        }

        result.sort()
        return result
    }
}
