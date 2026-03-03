package io.github.acedroidx.frp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class FrpConfigProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "io.github.acedroidx.frp.config"
        private const val CODE_CONFIGS = 1
        private const val CODE_CONFIG_ITEM = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            // 根路径列出全部配置：content://io.github.acedroidx.frp.config
            addURI(AUTHORITY, null, CODE_CONFIGS)
            // 单个配置：content://io.github.acedroidx.frp.config/{type}/{name}
            addURI(AUTHORITY, "*/*", CODE_CONFIG_ITEM)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val match = uriMatcher.match(uri)
        val context = context ?: return null
        val prefs = context.getSharedPreferences("data", android.content.Context.MODE_PRIVATE)

        // 仅在允许读取时暴露内容，避免敏感配置泄漏
        if (!prefs.getBoolean(PreferencesKey.ALLOW_CONFIG_READ, false)) {
            throw SecurityException("Config read not allowed")
        }

        val cursor = MatrixCursor(arrayOf("_id", "type", "name"))
        when (match) {
            CODE_CONFIGS -> {
                var id = 0L
                FrpType.entries.forEach { type ->
                    val names = type.getDir(context).list()?.toList() ?: emptyList()
                    names.forEach { name ->
                        cursor.addRow(arrayOf(id++, type.typeName, name))
                    }
                }
            }

            CODE_CONFIG_ITEM -> {
                val type = uri.pathSegments.getOrNull(0)
                val name = uri.pathSegments.getOrNull(1)
                if (type.isNullOrBlank() || name.isNullOrBlank()) {
                    return cursor
                }
                val frpType = AutoStartHelper.parseType(type)
                if (frpType != null) {
                    val file = File(frpType.getDir(context), name)
                    if (file.exists()) {
                        cursor.addRow(arrayOf(0L, frpType.typeName, name))
                    }
                }
            }

            else -> throw FileNotFoundException("Unknown URI: $uri")
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_CONFIGS -> "vnd.android.cursor.dir/vnd.io.github.acedroidx.frp.config"
            CODE_CONFIG_ITEM -> "text/plain"
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: throw FileNotFoundException("No context")
        val prefs = context.getSharedPreferences("data", android.content.Context.MODE_PRIVATE)
        val match = uriMatcher.match(uri)
        if (match != CODE_CONFIG_ITEM) {
            throw FileNotFoundException("Unsupported uri: $uri")
        }

        val type = uri.pathSegments.getOrNull(0)
        val name = uri.pathSegments.getOrNull(1)
        val frpType =
            AutoStartHelper.parseType(type) ?: throw FileNotFoundException("Invalid type: $type")
        if (name.isNullOrBlank()) throw FileNotFoundException("Invalid name")

        val modeBits = ParcelFileDescriptor.parseMode(mode)
        val isWrite = mode.contains("w") || mode.contains("a") || mode.contains("t")
        // 根据模式与开关判断是否允许读/写
        if (isWrite && !prefs.getBoolean(PreferencesKey.ALLOW_CONFIG_WRITE, false)) {
            throw SecurityException("Config write not allowed")
        }
        if (!isWrite && !prefs.getBoolean(PreferencesKey.ALLOW_CONFIG_READ, false)) {
            throw SecurityException("Config read not allowed")
        }

        val dir = frpType.getDir(context)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, name)
        if (!isWrite && !file.exists()) {
            throw FileNotFoundException("File not found")
        }
        return ParcelFileDescriptor.open(file, modeBits)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = context ?: return 0
        val prefs = context.getSharedPreferences("data", android.content.Context.MODE_PRIVATE)
        val match = uriMatcher.match(uri)

        // 删除属于写操作，必须显式允许
        if (!prefs.getBoolean(PreferencesKey.ALLOW_CONFIG_WRITE, false)) {
            throw SecurityException("Config write not allowed")
        }

        return when (match) {
            CODE_CONFIG_ITEM -> {
                val type = uri.pathSegments.getOrNull(0)
                val name = uri.pathSegments.getOrNull(1)
                if (type.isNullOrBlank() || name.isNullOrBlank()) {
                    return 0
                }
                val frpType = AutoStartHelper.parseType(type) ?: return 0
                val file = File(frpType.getDir(context), name)
                val deleted = if (file.exists()) file.delete() else false
                if (deleted) {
                    context.contentResolver.notifyChange(uri, null)
                    1
                } else {
                    0
                }
            }

            CODE_CONFIGS -> 0
            else -> throw FileNotFoundException("Unknown URI: $uri")
        }
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update not supported")
    }
}
