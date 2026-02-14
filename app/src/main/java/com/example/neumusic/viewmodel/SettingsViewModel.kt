package com.example.neumusic.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.neumusic.data.AudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    // 【统一修改】使用和 PlayerViewModel 一致的文件名和键名
    private val prefs = application.getSharedPreferences("music_settings", Context.MODE_PRIVATE)
    private val PREF_KEY_URI = "scan_uri_str"

    // 存储的是文件夹的 URI 字符串
    private val _scanUri = MutableStateFlow(prefs.getString(PREF_KEY_URI, "") ?: "")
    // (可选：如果 SettingsScreen 里用了 scanUri，保留它；如果只用了 scanPathDisplay，可以不暴露)

    // 用于 UI 展示的路径名称
    private val _scanPathDisplay = MutableStateFlow("未设置")
    val scanPathDisplay = _scanPathDisplay.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // 【修改】改为 List<String> 以存储多行日志
    private val _scanLogs = MutableStateFlow<List<String>>(emptyList())
    val scanLogs = _scanLogs.asStateFlow()

    init {
        if (_scanUri.value.isNotEmpty()) {
            try {
                updateDisplayPath(Uri.parse(_scanUri.value))
            } catch (e: Exception) {
                _scanPathDisplay.value = "路径失效"
            }
        }
    }

    fun setFolderUri(uri: Uri) {
        val contentResolver = getApplication<Application>().contentResolver
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        val uriString = uri.toString()
        prefs.edit().putString(PREF_KEY_URI, uriString).apply()
        _scanUri.value = uriString
        updateDisplayPath(uri)
    }

    private fun updateDisplayPath(uri: Uri) {
        val docFile = DocumentFile.fromTreeUri(getApplication(), uri)
        _scanPathDisplay.value = docFile?.name ?: uri.lastPathSegment ?: "已选择目录"
    }

    fun scanMusic(context: Context) {
        val uriStr = _scanUri.value
        if (_isScanning.value || uriStr.isEmpty()) return

        viewModelScope.launch {
            _isScanning.value = true
            _scanLogs.value = listOf("开始初始化...") // 清空并显示第一条

            val repo = AudioRepository(context)

            try {
                repo.scanUriAndSave(uriStr).collect { message ->
                    // 【修改】追加日志模式
                    val currentList = _scanLogs.value.toMutableList()
                    currentList.add(message)
                    _scanLogs.value = currentList
                }
            } catch (e: Exception) {
                val currentList = _scanLogs.value.toMutableList()
                currentList.add("扫描出错: ${e.localizedMessage}")
                _scanLogs.value = currentList
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearLog() {
        _scanLogs.value = emptyList()
    }
}
