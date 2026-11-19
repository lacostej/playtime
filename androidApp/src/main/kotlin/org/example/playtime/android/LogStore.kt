package org.example.playtime.android

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.playtime.api.ActivityLogger

/**
 * Local activity log for troubleshooting: the last [MAX] lines, on the device only
 * (noBackupFilesDir, excluded from backups). Callers must not log tokens, IDs or names.
 */
class LogStore(context: Context) : ActivityLogger {
    private val file = File(context.noBackupFilesDir, "activity.log")
    private val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow(runCatching { file.readLines().takeLast(MAX) }.getOrDefault(emptyList()))
    val lines: StateFlow<List<String>> = _lines

    @Synchronized
    override fun log(message: String) {
        val line = "${format.format(Date())} $message"
        if (BuildConfig.DEBUG) Log.d("playtime", line)
        val next = (_lines.value + line).takeLast(MAX)
        _lines.value = next
        runCatching { if (next.size == MAX) file.writeText(next.joinToString("\n", postfix = "\n")) else file.appendText(line + "\n") }
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        file.delete()
    }

    private companion object {
        const val MAX = 500
    }
}
