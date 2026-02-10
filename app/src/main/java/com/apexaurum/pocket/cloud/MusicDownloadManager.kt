package com.apexaurum.pocket.cloud

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.apexaurum.pocket.ui.screens.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads music tracks to device storage via MediaStore (API 29+)
 * or Music directory (API 26-28).
 */
class MusicDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    /** Download a track to device storage. */
    suspend fun downloadTrack(track: MusicTrack, url: String) {
        if (_downloads.value[track.id] == DownloadState.DOWNLOADING) return
        if (_downloads.value[track.id] == DownloadState.COMPLETE) return

        _downloads.value = _downloads.value + (track.id to DownloadState.DOWNLOADING)

        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw RuntimeException("Download failed: ${response.code}")
                }

                val body = response.body ?: throw RuntimeException("Empty response body")
                val filename = "${track.id}_${sanitizeFilename(track.title ?: "track")}.mp3"

                if (Build.VERSION.SDK_INT >= 29) {
                    // API 29+ — use MediaStore (no permission needed)
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ApexPocket")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw RuntimeException("Failed to create MediaStore entry")

                    resolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }

                    // Mark as complete
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } else {
                    // API 26-28 — write directly to Music directory
                    val musicDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        "ApexPocket"
                    )
                    musicDir.mkdirs()
                    val file = File(musicDir, filename)
                    file.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                }
            }
            _downloads.value = _downloads.value + (track.id to DownloadState.COMPLETE)
        } catch (_: Exception) {
            _downloads.value = _downloads.value + (track.id to DownloadState.FAILED)
        }
    }

    /** Check if a track has been downloaded and return its local URI string, or null. */
    fun getLocalUri(trackId: String): String? {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("${trackId}_%")

            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    return android.content.ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                }
            }
        } else {
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "ApexPocket"
            )
            musicDir.listFiles()?.find { it.name.startsWith("${trackId}_") }?.let {
                return it.toURI().toString()
            }
        }
        return null
    }

    /** Total size of downloaded ApexPocket music files in bytes. */
    fun getTotalSizeBytes(): Long {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Audio.Media.SIZE)
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%ApexPocket%")
            var total = 0L
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                while (cursor.moveToNext()) total += cursor.getLong(sizeCol)
            }
            return total
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "ApexPocket"
            )
            return dir.listFiles()?.sumOf { it.length() } ?: 0L
        }
    }

    /** Delete all downloaded ApexPocket music files. */
    fun clearAll(): Int {
        _downloads.value = emptyMap()
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%ApexPocket%")
            return resolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "ApexPocket"
            )
            var count = 0
            dir.listFiles()?.forEach { if (it.delete()) count++ }
            return count
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50)
    }
}
