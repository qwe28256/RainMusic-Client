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

    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // 存储的是文件夹的 URI 字符串 (例如: content://...)
    private val _scanUri = MutableStateFlow(prefs.getString("scan_uri", "") ?: "")
    val scanUri = _scanUri.asStateFlow()

    // 用于 UI 展示的路径名称 (例如: Music)
    private val _scanPathDisplay = MutableStateFlow("未设置")
    val scanPathDisplay = _scanPathDisplay.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // 新增：扫描日志/进度信息
    private val _scanLog = MutableStateFlow("")
    val scanLog = _scanLog.asStateFlow()

    init {
        // 初始化时更新显示名称
        if (_scanUri.value.isNotEmpty()) {
            try {
                updateDisplayPath(Uri.parse(_scanUri.value))
            } catch (e: Exception) {
                _scanPathDisplay.value = "无效路径"
            }
        }
    }

    // 保存用户选择的文件夹 URI
    fun setFolderUri(uri: Uri) {
        val contentResolver = getApplication<Application>().contentResolver

        // 关键步骤：请求持久化权限，否则重启后失效
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // 保存
        val uriString = uri.toString()
        prefs.edit().putString("scan_uri", uriString).apply()
        _scanUri.value = uriString
        updateDisplayPath(uri)
    }

    private fun updateDisplayPath(uri: Uri) {
        val docFile = DocumentFile.fromTreeUri(getApplication(), uri)
        // 尝试显示人类可读的名字
        _scanPathDisplay.value = docFile?.name ?: uri.lastPathSegment ?: "Unknown"
    }

    fun scanMusic(context: Context) {
        if (_isScanning.value || _scanUri.value.isEmpty()) return

        viewModelScope.launch {
            _isScanning.value = true
            _scanLog.value = "准备开始..."
            val repo = AudioRepository(context)

            try {
                // 【核心修改】：收集 Flow 实现实时日志
                repo.scanUriAndSave(_scanUri.value).collect { status ->
                    _scanLog.value = status // 实时更新 UI
                }
            } catch (e: Exception) {
                _scanLog.value = "扫描出错: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearLog() {
        _scanLog.value = ""
    }
}
