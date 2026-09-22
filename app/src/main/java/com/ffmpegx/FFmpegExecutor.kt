package com.ffmpegx

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class FFmpegExecutor(private val context: Context) {

    fun getBundledFFmpegPath(): String {
        val libraryDir = context.applicationInfo.nativeLibraryDir
        val binary = File(libraryDir, "libffmpeg.so")
        
        Log.d("FFmpegExecutor", "Scanning library dir: $libraryDir")
        val files = File(libraryDir).listFiles()
        files?.forEach { Log.d("FFmpegExecutor", "Found file: ${it.name} (${it.length()} bytes)") }
        
        if (binary.exists()) {
            Log.d("FFmpegExecutor", "Binary FOUND at: ${binary.absolutePath}")
            return binary.absolutePath
        } else {
            Log.e("FFmpegExecutor", "Binary NOT FOUND at: ${binary.absolutePath}")
            // Return the path anyway so we can see the OS error if it tries to run it
            return binary.absolutePath
        }
    }

    fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileName(uri) ?: "temp_${System.currentTimeMillis()}"
            val tempFile = File(context.cacheDir, fileName)
            
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = cursor.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) name = name?.substring(cut + 1)
        }
        return name?.replace(" ", "_")
    }

    fun executeCommand(binaryPath: String, commandArgs: String): Flow<String> = flow {
        try {
            val cmdList = mutableListOf(binaryPath)
            cmdList.addAll(parseArguments(commandArgs))

            val process = ProcessBuilder(cmdList)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line ?: "")
            }
            val exitCode = process.waitFor()
            emit("Process finished with exit code $exitCode")
        } catch (e: Exception) {
            emit("Error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    private fun parseArguments(command: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        var i = 0
        while (i < command.length) {
            val c = command[i]
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false
                } else {
                    current.append(c)
                }
            } else {
                if (c == '\"' || c == '\'') {
                    inQuotes = true
                    quoteChar = c
                } else if (c.isWhitespace()) {
                    if (current.isNotEmpty()) {
                        args.add(current.toString())
                        current.setLength(0)
                    }
                } else {
                    current.append(c)
                }
            }
            i++
        }
        if (current.isNotEmpty()) {
            args.add(current.toString())
        }
        return args
    }

    fun getFFmpegVersion(path: String): String {
        return try {
            val process = ProcessBuilder(path, "-version")
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var versionLine = "Unknown Version"
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: ""
                if (currentLine.lowercase().contains("version")) {
                    versionLine = currentLine
                    break
                }
            }
            process.destroy()
            versionLine.split(" Copyright").first().take(60)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
