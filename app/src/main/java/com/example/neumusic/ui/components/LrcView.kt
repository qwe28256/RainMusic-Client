package com.example.neumusic.ui.components

import androidx.compose.animation.core.AnimationSpec // 导入这个
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.neumusic.ui.AccentColor
import com.example.neumusic.ui.TextPrimary
import com.example.neumusic.utils.LyricsLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun LrcView(
    lyrics: List<LyricsLine>,
    currentTime: Long,
    isSeeking: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    var isUserInteracting by remember { mutableStateOf(false) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var isJustSeeked by remember { mutableStateOf(false) }

    val currentPlayIndex by remember(lyrics, currentTime) {
        derivedStateOf {
            if (lyrics.isEmpty()) -1
            else lyrics.indexOfLast { it.startTime <= currentTime }
        }
    }

    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf -1
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val closestItem = visibleItems.minByOrNull {
                abs((it.offset + it.size / 2) - viewportCenter)
            }
            closestItem?.index ?: -1
        }
    }

    LaunchedEffect(isListDragged) {
        if (isListDragged) {
            isUserInteracting = true
            isJustSeeked = false
            autoScrollJob?.cancel()
        } else {
            if (isUserInteracting) {
                autoScrollJob?.cancel()
                autoScrollJob = scope.launch {
                    delay(2000)
                    isUserInteracting = false
                    if (!isJustSeeked && currentPlayIndex >= 0) {
                        listState.animateScrollToItem(currentPlayIndex, scrollOffset = -150)
                    }
                    isJustSeeked = false
                }
            }
        }
    }

    LaunchedEffect(currentPlayIndex, isSeeking) {
        if (!isUserInteracting && !isJustSeeked && currentPlayIndex >= 0 && lyrics.isNotEmpty()) {
            if (isSeeking) {
                listState.scrollToItem(currentPlayIndex, scrollOffset = -150)
            } else {
                listState.animateScrollToItem(currentPlayIndex, scrollOffset = -150)
            }
        }
    }

    LaunchedEffect(isJustSeeked) {
        if (isJustSeeked) {
            delay(1000)
            isJustSeeked = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val halfHeight = maxHeight / 2

        if (lyrics.isEmpty()) {
            Text("暂无歌词", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = halfHeight, bottom = halfHeight),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isHighlighted = if (isUserInteracting) (index == centerIndex) else (index == currentPlayIndex)

                    // 【核心修复】显式指定泛型类型 <Float>，解决编译报错
                    val animationSpec: AnimationSpec<Float> = if (isSeeking) {
                        tween(0)
                    } else {
                        tween(durationMillis = 400, easing = FastOutSlowInEasing)
                    }

                    val scale by animateFloatAsState(
                        targetValue = if (isHighlighted) 1.3f else 1.0f,
                        animationSpec = animationSpec,
                        label = "scale"
                    )

                    val alpha by animateFloatAsState(
                        targetValue = if (isHighlighted) 1f else 0.5f,
                        animationSpec = animationSpec,
                        label = "alpha"
                    )

                    val textColor = if (isHighlighted) {
                        if (isUserInteracting) TextPrimary else AccentColor
                    } else {
                        TextPrimary
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                transformOrigin = TransformOrigin.Center
                            }
                    ) {
                        Text(
                            text = line.text,
                            color = textColor,
                            fontSize = 16.sp,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (isUserInteracting && centerIndex in lyrics.indices) {
                val centerLyricTime = lyrics[centerIndex].startTime

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.Center)
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.Center)) {
                        drawLine(
                            color = AccentColor.copy(alpha = 0.5f),
                            start = Offset(60.dp.toPx(), 0f),
                            end = Offset(size.width - 60.dp.toPx(), 0f),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .size(32.dp)
                            .background(AccentColor.copy(alpha = 0.1f), CircleShape)
                            .clickable {
                                onSeekTo(centerLyricTime)
                                isJustSeeked = true
                                isUserInteracting = false
                                autoScrollJob?.cancel()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Seek",
                            tint = AccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = formatTime(centerLyricTime),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
