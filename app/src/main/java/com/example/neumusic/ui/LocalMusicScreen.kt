package com.example.neumusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.neumusic.data.AudioFile
import com.example.neumusic.ui.theme.neumorphicShadow
import com.example.neumusic.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val displaySongs by viewModel.displaySongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showMenuForSong by remember { mutableStateOf<AudioFile?>(null) }

    // 删除确认弹窗的数据源 (这里还是存对象，方便显示歌名)
    var showDeleteDialog by remember { mutableStateOf<List<AudioFile>?>(null) }

    var isSearchMode by remember { mutableStateOf(false) }

    // === 多选模式状态 ===
    var isSelectionMode by remember { mutableStateOf(false) }
    // 【核心修复】改为存储 ID，避免对象引用问题
    val selectedIds = remember { mutableStateListOf<Long>() }

    BackHandler {
        when {
            isSelectionMode -> {
                isSelectionMode = false
                selectedIds.clear()
            }
            isSearchMode -> {
                isSearchMode = false
                viewModel.updateSearchQuery("")
            }
            else -> onBack()
        }
    }

    Scaffold(
        containerColor = NeuBackground,
        modifier = Modifier.statusBarsPadding(),
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                BottomAppBar(containerColor = NeuBackground) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    // 将 ID 映射回对象列表，用于显示弹窗
                                    val songsToDelete = displaySongs.filter { selectedIds.contains(it.id) }
                                    showDeleteDialog = songsToDelete
                                }
                            },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, null, tint = if (selectedIds.isNotEmpty()) Color.Red else Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("删除 (${selectedIds.size})", color = if (selectedIds.isNotEmpty()) Color.Red else Color.Gray)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            Column(modifier = Modifier.fillMaxWidth().background(NeuBackground)) {
                if (isSelectionMode) {
                    SelectionModeTopBar(
                        selectedCount = selectedIds.size,
                        totalCount = displaySongs.size,
                        onClose = {
                            isSelectionMode = false
                            selectedIds.clear()
                        },
                        onSelectAll = {
                            if (selectedIds.size == displaySongs.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(displaySongs.map { it.id })
                            }
                        }
                    )
                } else if (isSearchMode) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        onClose = {
                            isSearchMode = false
                            viewModel.updateSearchQuery("")
                        }
                    )
                } else {
                    LocalMusicTopBar(
                        onBack = onBack,
                        onSearchClick = { isSearchMode = true }
                    )
                }

                if (!isSearchMode && !isSelectionMode && displaySongs.isNotEmpty()) {
                    PlayAllHeader(
                        count = displaySongs.size,
                        onPlayAll = { viewModel.playFromPlaylist(0) }
                    )
                    Divider(color = Color.Gray.copy(0.1f), thickness = 1.dp)
                }
            }

            if (displaySongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到歌曲", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(displaySongs) { index, song ->
                        val isPlaying = song.id == currentSong?.id
                        // 【核心修复】通过 ID 判断选中状态
                        val isSelected = selectedIds.contains(song.id)

                        SongListItem(
                            index = index + 1,
                            song = song,
                            isPlaying = isPlaying,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onItemClick = {
                                if (isSelectionMode) {
                                    // 【核心修复】Toggle 逻辑：有则删，无则加
                                    if (selectedIds.contains(song.id)) {
                                        selectedIds.remove(song.id)
                                        // 可选：如果全取消了，自动退出多选 (目前保留在多选模式)
                                    } else {
                                        selectedIds.add(song.id)
                                    }
                                } else {
                                    viewModel.playAudioFile(song)
                                }
                            },
                            onItemLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedIds.add(song.id)
                                }
                            },
                            onMoreClick = { showMenuForSong = song }
                        )
                    }
                }
            }
        }
    }

    // 底部菜单 (单曲操作)
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    if (showMenuForSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showMenuForSong = null },
            sheetState = sheetState,
            containerColor = NeuBackground
        ) {
            Column(modifier = Modifier.padding(bottom = 40.dp)) {
                Text(
                    text = "歌曲：${showMenuForSong?.title}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
                Divider(color = Color.Gray.copy(0.1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val songToDelete = showMenuForSong
                            showMenuForSong = null
                            scope.launch { showDeleteDialog = listOf(songToDelete!!) }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("删除", fontSize = 16.sp, color = TextPrimary)
                }
            }
        }
    }

    // 批量删除弹窗
    if (showDeleteDialog != null) {
        val count = showDeleteDialog!!.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(if (count > 1) "批量删除" else "删除歌曲", color = TextPrimary) },
            text = {
                Text(
                    if (count > 1) "确定要删除这 $count 首歌曲吗？\n文件也将被永久删除。"
                    else "确定要删除“${showDeleteDialog!![0].title}”吗？\n文件也将被永久删除。",
                    color = TextPrimary
                )
            },
            containerColor = NeuBackground,
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog!!.forEach { song ->
                            viewModel.deleteAudioFile(song)
                        }
                        isSelectionMode = false
                        selectedIds.clear()
                        showDeleteDialog = null
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }
}

// === 多选模式标题栏 ===
@Composable
fun SelectionModeTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NeuIconButton(icon = Icons.Default.Close, onClick = onClose)

        Text(
            text = if (selectedCount == 0) "选择歌曲" else "已选择 $selectedCount 项",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        TextButton(onClick = onSelectAll) {
            Text(
                text = if (selectedCount == totalCount) "全不选" else "全选",
                color = AccentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// === 列表项 (包含多选逻辑) ===
@Composable
fun SongListItem(
    index: Int,
    song: AudioFile,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onItemClick() },
                    onLongPress = { onItemLongClick() }
                )
            }
            .background(if (isSelected) AccentColor.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onItemClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentColor,
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            if (isPlaying) {
                Icon(Icons.Default.PlayArrow, null, tint = AccentColor, modifier = Modifier.width(24.dp).size(16.dp))
            } else {
                Text(
                    text = "$index",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(0.7f),
                    modifier = Modifier.width(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) AccentColor else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${song.artist} - 未知专辑",
                fontSize = 12.sp,
                color = if (isPlaying) AccentColor.copy(0.7f) else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onMoreClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LocalMusicTopBar(onBack: () -> Unit, onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NeuIconButton(icon = Icons.Default.ArrowBack, onClick = onBack)
        Text("本地音乐", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        NeuIconButton(icon = Icons.Default.Search, onClick = onSearchClick)
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp).height(50.dp)
            .neumorphicShadow(NeuLightShadow, NeuDarkShadow, 2.dp, 25.dp)
            .background(NeuBackground, CircleShape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f),
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
            cursorBrush = SolidColor(AccentColor), singleLine = true,
            decorationBox = { inner -> if (query.isEmpty()) Text("搜索本地歌曲...", color = Color.Gray.copy(0.5f)); inner() }
        )
        if (query.isNotEmpty()) Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.clickable { onQueryChange("") })
        else Text("取消", color = AccentColor, modifier = Modifier.clickable { onClose() })
    }
}

@Composable
fun PlayAllHeader(count: Int, onPlayAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onPlayAll() }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).neumorphicShadow(NeuLightShadow, NeuDarkShadow, 4.dp, 22.dp).background(AccentColor, CircleShape)) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column { Text("播放全部", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text("(共 $count 首)", fontSize = 12.sp, color = Color.Gray) }
    }
}

@Composable
fun NeuIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).neumorphicShadow(NeuLightShadow, NeuDarkShadow, 4.dp, 20.dp).background(NeuBackground, CircleShape).clip(CircleShape).clickable { onClick() }) {
        Icon(icon, null, tint = TextPrimary)
    }
}
