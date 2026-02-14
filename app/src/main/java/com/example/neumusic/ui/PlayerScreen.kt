package com.example.neumusic.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neumusic.R
import com.example.neumusic.data.AudioFile
import com.example.neumusic.ui.components.LrcView
import com.example.neumusic.ui.theme.neumorphicShadow
import com.example.neumusic.utils.LyricsLine
import com.example.neumusic.viewmodel.PlayMode
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
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    // 状态控制
    var showTimerSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCustomTimeDialog by remember { mutableStateOf(false) }

    // 【新增】状态上提：拖动进度
    var draggingProgress by remember { mutableStateOf<Float?>(null) }

    val timerSheetState = rememberModalBottomSheetState()
    val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBackground)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Bar
            PlayerTopBar(viewModel, pagerState, onCollapse)

            // 2. Pager (封面/歌词)
            PlayerContentPager(
                viewModel = viewModel,
                pagerState = pagerState,
                draggingProgress = draggingProgress, // 传入拖动进度实现歌词预览
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 中间功能区
            Box(modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    SleepTimerIcon(viewModel) { showTimerSheet = true }
                }
                Box(modifier = Modifier.align(Alignment.Center)) {
                    PageIndicator(pagerState)
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayModeIcon(viewModel)
                    Spacer(modifier = Modifier.width(16.dp))
                    PlaylistIcon { showPlaylistSheet = true }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Progress (状态上提)
            PlayerProgressSection(
                viewModel = viewModel,
                draggingProgress = draggingProgress,
                onDraggingChange = { draggingProgress = it },
                onDragFinished = {
                    draggingProgress?.let { viewModel.seekTo(it.toLong()) }
                    draggingProgress = null
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Controls
            PlayerControlsSection(viewModel)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Sheets
    if (showTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTimerSheet = false },
            sheetState = timerSheetState,
            containerColor = NeuBackground
        ) {
            SleepTimerSheetContent(
                onTimeSelected = { min -> viewModel.setSleepTimer(min); showTimerSheet = false },
                onCustomClick = { showTimerSheet = false; showCustomTimeDialog = true },
                onCancel = { viewModel.cancelSleepTimer(); showTimerSheet = false }
            )
        }
    }

    if (showPlaylistSheet) {
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val sheetHeight = screenHeight * 0.6f

        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            sheetState = playlistSheetState,
            containerColor = NeuBackground,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.statusBarsPadding(),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Box(modifier = Modifier.height(sheetHeight)) {
                PlaylistBottomSheetContent(
                    viewModel = viewModel,
                    onClose = {
                        scope.launch { playlistSheetState.hide() }.invokeOnCompletion {
                            if (!playlistSheetState.isVisible) showPlaylistSheet = false
                        }
                    }
                )
            }
        }
    }

    if (showCustomTimeDialog) {
        CustomTimeDialog(
            onConfirm = { min -> viewModel.setSleepTimer(min); showCustomTimeDialog = false },
            onDismiss = { showCustomTimeDialog = false }
        )
    }
}

// === 修改：PlayerContentPager (接收拖动进度) ===
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerContentPager(
    viewModel: PlayerViewModel,
    pagerState: PagerState,
    draggingProgress: Float?,
    modifier: Modifier
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val realProgress by viewModel.progress.collectAsState()

    // 1. 判断是否正在拖动 (draggingProgress 不为 null 说明正在拖动)
    val isSeeking = draggingProgress != null

    // 2. 决定传给歌词控件的时间：如果是拖动中，就用拖动的值；否则用播放器真实进度
    val effectiveProgress = draggingProgress?.toLong() ?: realProgress

    HorizontalPager(
        state = pagerState,
        modifier = modifier
    ) { page ->
        if (page == 0) {
            PlayerMainContent(currentSong)
        } else {
            // 3. 传入所有参数
            LrcView(
                lyrics = lyrics,
                currentTime = effectiveProgress,
                isSeeking = isSeeking, // 【修复点】传入这个缺失的参数
                onSeekTo = { time -> viewModel.seekTo(time) }
            )
        }
    }
}


// === 修改：PlayerProgressSection (状态上提) ===
@Composable
fun PlayerProgressSection(
    viewModel: PlayerViewModel,
    draggingProgress: Float?,
    onDraggingChange: (Float?) -> Unit,
    onDragFinished: () -> Unit
) {
    val realProgress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()

    val currentPos = draggingProgress ?: realProgress.toFloat()
    val totalDuration = if (duration > 0) duration.toFloat() else 1f

    fun formatTime(ms: Long) = String.format("%02d:%02d", ms / 1000 / 60, (ms / 1000) % 60)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = currentPos,
            onValueChange = onDraggingChange, // 拖动时只更新状态，不 Seek
            onValueChangeFinished = onDragFinished, // 松手时 Seek
            valueRange = 0f..totalDuration,
            colors = SliderDefaults.colors(
                thumbColor = AccentColor,
                activeTrackColor = AccentColor,
                inactiveTrackColor = Color.Gray.copy(0.2f)
            ),
            modifier = Modifier.height(20.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPos.toLong()), color = TextPrimary, fontSize = 12.sp)
            Text(formatTime(duration), color = TextPrimary, fontSize = 12.sp)
        }
    }
}

// === 其他组件 ===

@Composable
fun PlayModeIcon(viewModel: PlayerViewModel) {
    val mode by viewModel.playMode.collectAsState()
    val context = LocalContext.current

    val icon = when (mode) {
        PlayMode.LOOP_ALL -> Icons.Default.Repeat
        PlayMode.LOOP_ONE -> Icons.Default.RepeatOne
        PlayMode.SHUFFLE -> Icons.Default.Shuffle
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .neumorphicShadow(NeuLightShadow, NeuDarkShadow, 4.dp, 18.dp)
            .background(NeuBackground, CircleShape)
            .clip(CircleShape)
            .clickable {
                viewModel.togglePlayMode()
                val nextText = when(mode) {
                    PlayMode.LOOP_ALL -> "单曲循环"
                    PlayMode.LOOP_ONE -> "随机播放"
                    PlayMode.SHUFFLE -> "列表循环"
                }
                Toast.makeText(context, nextText, Toast.LENGTH_SHORT).show()
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Play Mode",
            tint = if (mode == PlayMode.SHUFFLE) AccentColor else TextPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageIndicator(pagerState: PagerState) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pagerState.pageCount) { iteration ->
            val isSelected = pagerState.currentPage == iteration
            val width by animateDpAsState(targetValue = if (isSelected) 16.dp else 6.dp, label = "dot")
            val color = if (isSelected) AccentColor else Color.Gray.copy(alpha = 0.4f)

            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun PlaylistIcon(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .neumorphicShadow(NeuLightShadow, NeuDarkShadow, 4.dp, 18.dp)
            .background(NeuBackground, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = Icons.Default.List,
            contentDescription = "Playlist",
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

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
            Text(text = timerText!!, fontSize = 10.sp, color = AccentColor, fontWeight = FontWeight.Bold)
        } else {
            Icon(imageVector = Icons.Default.Schedule, contentDescription = "Timer", tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

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
                    .crossfade(false)
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

@Composable
fun PlaylistBottomSheetContent(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val playlist by viewModel.playlist.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playMode by viewModel.playMode.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(currentSong) {
        val index = playlist.indexOfFirst { it.id == currentSong?.id }
        if (index != -1) {
            listState.scrollToItem(index)
        }
    }

    val modeText = when(playMode) {
        PlayMode.LOOP_ALL -> "列表循环"
        PlayMode.LOOP_ONE -> "单曲循环"
        PlayMode.SHUFFLE -> "随机播放"
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "当前播放 (${playlist.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = modeText,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Divider(color = Color.Gray.copy(0.1f))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(items = playlist, key = { _, item -> item.id }) { index, song ->
                val isPlaying = song.id == currentSong?.id
                val textColor = if (isPlaying) AccentColor else TextPrimary

                Row(
                    modifier = Modifier.fillMaxWidth().height(50.dp).clickable { viewModel.playFromPlaylist(index) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlaying) {
                        Icon(Icons.Default.PlayArrow, "Playing", tint = AccentColor, modifier = Modifier.size(16.dp).padding(end = 8.dp))
                    } else {
                        Spacer(modifier = Modifier.width(24.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(song.title, color = textColor, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(" - ${song.artist}", color = if (isPlaying) AccentColor.copy(0.7f) else Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    IconButton(onClick = { viewModel.removeFromPlaylist(song) }) {
                        Icon(painter = painterResource(id = R.drawable.ic_music_close), contentDescription = "Remove", tint = Color.Gray.copy(0.5f), modifier = Modifier.size(16.dp))
                    }
                }
                Divider(color = Color.Gray.copy(0.05f))
            }
        }
    }
}
