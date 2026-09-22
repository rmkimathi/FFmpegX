package com.ffmpegx

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStorePublisher(private val context: Context) {

    suspend fun publishDirectoryContent(directoryPath: String) = withContext(Dispatchers.IO) {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext
        
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                publishToPublic(file)
            }
        }
    }

    private suspend fun publishToPublic(privateFile: File) = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val extension = privateFile.extension.lowercase()
            val mimeType = when (extension) {
                "aac", "flac", "m4a", "mp3", "ogg", "oga", "opus", "wav", "wma" -> "audio/*"
                "3gp", "avi", "m4v", "mkv", "mp4", "mov", "mpeg", "ts", "webm" -> "video/*"
                else -> "application/octet-stream"
            }
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, privateFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val folder = if (mimeType.startsWith("audio")) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/FFmpegX")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (mimeType.startsWith("audio")) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                if (mimeType.startsWith("audio")) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    privateFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                
                MediaScannerConnection.scanFile(context, arrayOf(privateFile.absolutePath), null) { _, _ -> }
                privateFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
