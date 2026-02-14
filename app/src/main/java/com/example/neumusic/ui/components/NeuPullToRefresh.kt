package com.example.neumusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.neumusic.ui.AccentColor
import com.example.neumusic.ui.NeuBackground

/**
 * 通用下拉刷新容器 (Neumorphic 风格适配)
 * @param isRefreshing 是否正在刷新
 * @param onRefresh 触发刷新的回调
 * @param content 列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            // 自定义指示器样式
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                containerColor = NeuBackground, // 背景色
                color = AccentColor,            // 转圈颜色
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content
    )
}
