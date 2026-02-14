package com.example.neumusic.utils

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.charset.Charset
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

    /**
     * 【终极正则】
     * 1. \s* : 允许在任何符号周围出现空格
     * 2. \d+ : 允许数字是 1位、2位或多位 (兼容 [3:5.0])
     * 3. (?: ... )? : 毫秒部分是可选的
     */
    private val TIME_PATTERN = Pattern.compile("\\[\\s*(\\d+)\\s*:\\s*(\\d+)(?:\\s*[.:]\\s*(\\d+))?\\s*]")

    private val TAG_PATTERN = Pattern.compile("\\[(ti|ar|al|by|offset):.*?\\]")

    fun getLyrics(audioPath: String): List<LyricsLine> {
        var rawLyrics = getEmbeddedLyrics(audioPath)

        if (rawLyrics.isNullOrEmpty()) {
            rawLyrics = getLrcFileContent(audioPath)
        }

        if (rawLyrics.isNullOrEmpty()) {
            return emptyList()
        }

        return parseLrc(rawLyrics)
    }

    private fun getEmbeddedLyrics(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null
            val lyrics = tag.getFirst(FieldKey.LYRICS)
            if (lyrics.isNotBlank()) lyrics else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getLrcFileContent(path: String): String? {
        return try {
            val lrcPath = path.substringBeforeLast(".") + ".lrc"
            val lrcFile = File(lrcPath)
            if (lrcFile.exists() && lrcFile.canRead()) {
                readTextWithAutoEncoding(lrcFile)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readTextWithAutoEncoding(file: File): String {
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                file.readText(Charset.forName("GBK"))
            } catch (e2: Exception) {
                ""
            }
        }
    }

    private fun parseLrc(lrcContent: String): List<LyricsLine> {
        val result = ArrayList<LyricsLine>()

        // 【第一步：全局清洗】
        // 这里的关键是把 \u00A0 (NBSP) 和 \u3000 (全角空格) 统统变成标准空格
        // 这样后续的正则 \s 就能识别它们了
        val cleanContent = lrcContent
            .replace('\u00A0', ' ')
            .replace('\u3000', ' ')
            .replace('\uFEFF', ' ') // 去除 BOM
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val lines = cleanContent.split("\n")

        for (line in lines) {
            val trimLine = line.trim()
            if (trimLine.isEmpty()) continue

            // 过滤元数据
            if (TAG_PATTERN.matcher(trimLine).find()) continue

            val matcher = TIME_PATTERN.matcher(trimLine)
            val timeStamps = ArrayList<Long>()

            while (matcher.find()) {
                try {
                    val min = matcher.group(1)?.toLong() ?: 0
                    val sec = matcher.group(2)?.toLong() ?: 0
                    val msStr = matcher.group(3)

                    // 毫秒处理逻辑 (兼容您提供的 [03:12.0])
                    val ms = when (msStr?.length) {
                        null -> 0L
                        1 -> msStr.toLong() * 100  // .0 -> 0ms, .5 -> 500ms
                        2 -> msStr.toLong() * 10   // .50 -> 500ms
                        else -> msStr.take(3).toLong() // 截取前3位
                    }

                    val time = (min * 60 * 1000) + (sec * 1000) + ms
                    timeStamps.add(time)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (timeStamps.isEmpty()) continue

            // 移除所有时间标签，剩下的就是歌词文本
            var content = matcher.replaceAll("").trim()

            // 去除注释
            if (content.contains("//")) {
                content = content.substringBefore("//").trim()
            }

            // 如果这一行是空的（比如只有时间戳的空行），是否保留？
            // 您的样本中有空行，如果想让空行也占位（显示一段空白时间），就保留
            // 这里为了界面美观，如果是空字符串，通常不添加，除非您希望显示间隔
            if (content.isEmpty()) {
                // 可选：如果是段落间奏，可以加一个空字符串进去
                // result.add(LyricsLine(timeStamps[0], ""))
                continue
            }

            for (time in timeStamps) {
                result.add(LyricsLine(time, content))
            }
        }

        result.sort()
        return result
    }
}
