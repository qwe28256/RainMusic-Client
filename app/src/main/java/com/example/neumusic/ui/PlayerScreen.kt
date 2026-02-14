package com.example.neumusic.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neumusic.data.AudioFile
import com.example.neumusic.ui.theme.neumorphicShadow
import com.example.neumusic.utils.LyricsLine
import com.example.neumusic.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

// === 颜色定义 ===
val NeuBackground = Color(0xFFE0E5EC)
val NeuDarkShadow = Color(0xFFA3B1C6)
val NeuLightShadow = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF4A5568)
val AccentColor = Color(0xFF7F77F9)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit
) {
    // 性能优化：只在这里 collect 那些不会频繁变化的 State
    // progress 等高频 State 下沉到子组件
    val pagerState = rememberPagerState(pageCount = { 2 })

    // 定时器状态
    var showTimerSheet by remember { mutableStateOf(false) }
    var showCustomTimeDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBackground)
            .statusBarsPadding()
            .padding(24.dp)
        // 移除 graphicsLayer 以节省内存，除非有复杂动画需求
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Bar (歌曲标题在此)
            PlayerTopBar(viewModel, pagerState, onCollapse)

            // 2. Pager (封面/歌词)
            // 开启 beyondBoundsPageCount 预加载，确保滑动不卡顿
            PlayerContentPager(
                viewModel = viewModel,
                pagerState = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Indicator + Timer Icon
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // 左侧悬浮：定时器图标
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    SleepTimerIcon(viewModel) { showTimerSheet = true }
                }

                // 中间：页面指示器
                PageIndicator(pagerState)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Progress (高频刷新区，独立组件)
            PlayerProgressSection(viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Controls
            PlayerControlsSection(viewModel)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // === Bottom Sheet ===
    if (showTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTimerSheet = false },
            sheetState = sheetState,
            containerColor = NeuBackground
        ) {
            SleepTimerSheetContent(
                onTimeSelected = { min ->
                    viewModel.setSleepTimer(min)
                    showTimerSheet = false
                },
                onCustomClick = {
                    showTimerSheet = false
                    showCustomTimeDialog = true
                },
                onCancel = {
                    viewModel.cancelSleepTimer()
                    showTimerSheet = false
                }
            )
        }
    }

    // === Custom Time Dialog ===
    if (showCustomTimeDialog) {
        CustomTimeDialog(
            onConfirm = { min ->
                viewModel.setSleepTimer(min)
                showCustomTimeDialog = false
            },
            onDismiss = { showCustomTimeDialog = false }
        )
    }
}

// === 组件：定时器图标 ===
@Composable
fun SleepTimerIcon(viewModel: PlayerViewModel, onClick: () -> Unit) {
    val timerText by viewModel.sleepTimerText.collectAsState()
    val isActive = timerText != null

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .neumorphicShadow(NeuLightShadow, NeuDarkShadow, 4.dp, 18.dp)
            .background(NeuBackground, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        if (isActive) {
            // 倒计时激活：显示时间
            Text(
                text = timerText!!,
                fontSize = 10.sp,
                color = AccentColor,
                fontWeight = FontWeight.Bold
            )
        } else {
            // 未激活：显示时钟图标
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Timer",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// === 组件：定时器选择面板 ===
@Composable
fun SleepTimerSheetContent(
    onTimeSelected: (Int) -> Unit,
    onCustomClick: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp, start = 24.dp, end = 24.dp)) {
        Text("定时关闭", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 16.dp))

        listOf(10, 20, 30).forEach { min ->
            Row(
                modifier = Modifier.fillMaxWidth().height(50.dp).clickable { onTimeSelected(min) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$min 分钟", fontSize = 16.sp, color = TextPrimary)
            }
            Divider(color = Color.Gray.copy(alpha = 0.1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(50.dp).clickable { onCustomClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Edit, null, tint = AccentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("自定义时间...", fontSize = 16.sp, color = AccentColor)
        }
        Divider(color = Color.Gray.copy(alpha = 0.1f))

        Row(
            modifier = Modifier.fillMaxWidth().height(50.dp).clickable { onCancel() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("关闭定时", fontSize = 16.sp, color = Color.Red.copy(alpha = 0.7f))
        }
    }
}

// === 组件：自定义时间弹窗 ===
@Composable
fun CustomTimeDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入时间 (分钟)", color = TextPrimary, fontSize = 18.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.all { char -> char.isDigit() }) text = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = AccentColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val min = text.toIntOrNull()
                if (min != null && min > 0) onConfirm(min)
            }) { Text("确定", color = AccentColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color.Gray) }
        },
        containerColor = NeuBackground
    )
}

// === 组件：顶部栏 ===
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerTopBar(viewModel: PlayerViewModel, pagerState: PagerState, onCollapse: () -> Unit) {
    val currentSong by viewModel.currentSong.collectAsState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        NeuButton(size = 40.dp, onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, "Collapse", tint = TextPrimary)
        }

        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.6f).clickable {
                scope.launch {
                    val nextPage = if (pagerState.currentPage == 0) 1 else 0
                    pagerState.animateScrollToPage(nextPage)
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentSong?.title ?: "No Song",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong?.artist ?: "Unknown",
                color = Color.Gray,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// === 组件：Pager ===
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerContentPager(viewModel: PlayerViewModel, pagerState: PagerState, modifier: Modifier) {
    val currentSong by viewModel.currentSong.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsState()

    HorizontalPager(
        state = pagerState,
        beyondBoundsPageCount = 1, // 保持预加载
        modifier = modifier
    ) { page ->
        if (page == 0) {
            PlayerMainContent(currentSong)
        } else {
            LyricsView(
                lyrics = lyrics,
                currentIndex = currentLyricIndex,
                onSeekToLine = { time -> viewModel.seekTo(time) }
            )
        }
    }
}

@Composable
fun PlayerMainContent(currentSong: AudioFile?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NeuAlbumCover(currentSong)
    }
}

@Composable
fun NeuAlbumCover(song: AudioFile?) {
    val boxSize = 240.dp
    val imageSize = 220.dp
    val coverRadius = 60.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(boxSize).neumorphicShadow(NeuLightShadow, NeuDarkShadow, 15.dp, coverRadius).background(NeuBackground, RoundedCornerShape(coverRadius))
    ) {
        if (song != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri)
                    .crossfade(false) // 保持关闭 crossfade 以优化性能
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(imageSize).clip(RoundedCornerShape(coverRadius - 10.dp))
            )
        } else {
            Box(
                modifier = Modifier.size(imageSize).background(Color.Gray.copy(0.1f), RoundedCornerShape(coverRadius - 10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Gray.copy(0.5f), modifier = Modifier.size(50.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageIndicator(pagerState: PagerState) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(pagerState.pageCount) { iteration ->
            val isSelected = pagerState.currentPage == iteration
            val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, label = "dot")
            val color = if (isSelected) AccentColor else Color.Gray.copy(alpha = 0.4f)
            Box(modifier = Modifier.padding(4.dp).height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun LyricsView(lyrics: List<LyricsLine>, currentIndex: Int, onSeekToLine: (Long) -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && lyrics.isNotEmpty()) {
            listState.animateScrollToItem(index = currentIndex, scrollOffset = -300)
        }
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Lyrics", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 150.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = lyrics,
                // 保持 Key 唯一性修复
                key = { i, item -> "${item.startTime}_$i" },
                // 保持 contentType 优化
                contentType = { _, _ -> "lyric" }
            ) { index, line ->
                val isCurrent = index == currentIndex
                val color = if (isCurrent) AccentColor else Color.Gray.copy(0.5f)
                val size = if (isCurrent) 24.sp else 16.sp
                val weight = if (isCurrent) FontWeight.Bold else FontWeight.Medium

                Text(
                    text = line.text,
                    color = color,
                    fontSize = size,
                    fontWeight = weight,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 20.dp)
                        .clickable { onSeekToLine(line.startTime) }
                )
            }
        }
    }
}

@Composable
fun PlayerProgressSection(viewModel: PlayerViewModel) {
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()

    fun formatTime(ms: Long) = String.format("%02d:%02d", ms / 1000 / 60, (ms / 1000) % 60)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (duration > 0) progress.toFloat() else 0f,
            onValueChange = { viewModel.seekTo(it.toLong()) },
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor, inactiveTrackColor = Color.Gray.copy(0.2f)),
            modifier = Modifier.height(20.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(progress), color = TextPrimary, fontSize = 12.sp)
            Text(formatTime(duration), color = TextPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
fun PlayerControlsSection(viewModel: PlayerViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        NeuButton(60.dp, { viewModel.playPrevious() }) { Icon(Icons.Default.SkipPrevious, null, tint = TextPrimary) }
        NeuButton(80.dp, { viewModel.togglePlayPause() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = AccentColor, modifier = Modifier.size(40.dp)) }
        NeuButton(60.dp, { viewModel.playNext() }) { Icon(Icons.Default.SkipNext, null, tint = TextPrimary) }
    }
}

@Composable
fun NeuButton(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).neumorphicShadow(NeuLightShadow, NeuDarkShadow, 8.dp, size / 2).background(NeuBackground, CircleShape).clip(CircleShape).clickable { onClick() }
    ) { content() }
}
