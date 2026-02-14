package com.example.neumusic.data

import android.content.Context

// 由于引入了 Room 数据库，音乐数据的持久化和缓存由数据库自动处理。
// AppContainer 不再需要手动缓存 List<AudioFile>。
object AppContainer {

    // 目前不需要做任何预加载动作，因为：
    // 1. 启动时直接读数据库（毫秒级）。
    // 2. 数据库操作在 Repository 内部处理。
    fun preloadData(context: Context) {
        // 留空，或者用于初始化其他非数据库的单例组件
    }
}
