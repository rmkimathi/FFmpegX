package com.ffmpegx

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val executor = FFmpegExecutor(context)
    private val publisher = MediaStorePublisher(context)
    private val sharedPrefs = context.getSharedPreferences("ffmpeg_prefs_v2", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Int>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    data class UiState(
        val input1: String = "",
        val input2: String = "",
        val binaryPath: String = "Built-in (libffmpeg.so)",
        val binaryVersion: String = "Detecting...",
        val templates: List<CommandTemplate> = emptyList(),
        val selectedTemplateIndex: Int = 0,
        val consoleOutput: String = "Console ready...\n",
        val isExecuting: Boolean = false,
        val currentTemplateName: String = "",
        val currentTemplatePattern: String = "",
        val reviewCommand: String = ""
    )

    init {
        loadTemplates()
        loadBinaryPath()
    }

    private fun loadTemplates() {
        val saved = sharedPrefs.getString("saved_templates", null)
        val templates = if (saved.isNullOrEmpty()) {
            listOf(
                CommandTemplate("Remux (Copy)", "-i {input1} -i {input2} -c copy {output_dir}/out.mp4"),
                CommandTemplate("Merge Audio/Video", "-i {input1} -i {input2} -c:v copy -c:a aac {output_dir}/merged.mp4")
            )
        } else {
            saved.split(";;").mapNotNull {
                val parts = it.split("||")
                if (parts.size == 2) CommandTemplate(parts[0], parts[1]) else null
            }
        }
        _uiState.update { it.copy(templates = templates) }
        saveTemplates(templates)
        updateCurrentTemplate(0)
    }

    private fun saveTemplates(templates: List<CommandTemplate>) {
        val serialized = templates.joinToString(";;") { "${it.name}||${it.commandPattern}" }
        sharedPrefs.edit().putString("saved_templates", serialized).apply()
    }

    private fun loadBinaryPath() {
        val path = executor.getBundledFFmpegPath()
        _uiState.update { it.copy(binaryPath = path) }
        updateBinaryVersion(path)
    }

    fun setInput1(uri: Uri) {
        val path = executor.getPathFromUri(uri)
        if (path != null) {
            _uiState.update { it.copy(input1 = path) }
        }
    }

    fun setInput2(uri: Uri) {
        val path = executor.getPathFromUri(uri)
        if (path != null) {
            _uiState.update { it.copy(input2 = path) }
        }
    }

    private fun updateBinaryVersion(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val version = executor.getFFmpegVersion(path)
            _uiState.update { it.copy(binaryVersion = version) }
        }
    }

    fun updateCurrentTemplate(index: Int) {
        if (index in _uiState.value.templates.indices) {
            val template = _uiState.value.templates[index]
            _uiState.update { it.copy(
                selectedTemplateIndex = index,
                currentTemplateName = template.name,
                currentTemplatePattern = template.commandPattern
            ) }
        }
    }

    fun editTemplateName(name: String) {
        _uiState.update { it.copy(currentTemplateName = name) }
    }

    fun editTemplatePattern(pattern: String) {
        _uiState.update { it.copy(currentTemplatePattern = pattern) }
    }

    fun saveCurrentTemplate() {
        val currentState = _uiState.value
        val newTemplates = currentState.templates.toMutableList()
        val index = newTemplates.indexOfFirst { it.name.equals(currentState.currentTemplateName, ignoreCase = true) }
        val newTemplate = CommandTemplate(currentState.currentTemplateName, currentState.currentTemplatePattern)
        
        if (index != -1) {
            newTemplates[index] = newTemplate
        } else {
            newTemplates.add(newTemplate)
        }
        
        _uiState.update { it.copy(templates = newTemplates) }
        saveTemplates(newTemplates)
    }

    fun deleteSelectedTemplate() {
        val currentState = _uiState.value
        if (currentState.templates.size > 1) {
            val newTemplates = currentState.templates.toMutableList()
            newTemplates.removeAt(currentState.selectedTemplateIndex)
            _uiState.update { it.copy(templates = newTemplates, selectedTemplateIndex = 0) }
            updateCurrentTemplate(0)
            saveTemplates(newTemplates)
        }
    }

    fun importTemplates(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = inputStream.bufferedReader()
                    val newTemplates = mutableListOf<CommandTemplate>()
                    reader.forEachLine { line ->
                        val parts = line.split(": ", limit = 2)
                        if (parts.size == 2) {
                            newTemplates.add(CommandTemplate(parts[0], parts[1]))
                        }
                    }
                    withContext(Dispatchers.Main) {
                        val currentTemplates = _uiState.value.templates.toMutableList()
                        newTemplates.forEach { newTemp ->
                            val existingIndex = currentTemplates.indexOfFirst { it.name.equals(newTemp.name, ignoreCase = true) }
                            if (existingIndex != -1) {
                                currentTemplates[existingIndex] = newTemp
                            } else {
                                currentTemplates.add(newTemp)
                            }
                        }
                        _uiState.update { it.copy(templates = currentTemplates) }
                        saveTemplates(currentTemplates)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportTemplates(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val content = _uiState.value.templates.joinToString("\n") { "${it.name}: ${it.commandPattern}" }
                    outputStream.write(content.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateReviewCommand() {
        val state = _uiState.value
        val outputDir = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
        val command = state.currentTemplatePattern
            .replace("{input1}", if (state.input1.isEmpty()) "[INPUT1]" else "\"${state.input1}\"")
            .replace("{input2}", if (state.input2.isEmpty()) "[INPUT2]" else "\"${state.input2}\"")
            .replace("{output_dir}", "\"$outputDir\"")
        _uiState.update { it.copy(reviewCommand = command) }
    }

    fun setReviewCommand(command: String) {
        _uiState.update { it.copy(reviewCommand = command) }
    }

    fun runCommand() {
        val state = _uiState.value
        if (state.reviewCommand.isEmpty()) return

        _uiState.update { it.copy(isExecuting = true, consoleOutput = "Executing command...\n") }
        
        viewModelScope.launch {
            _navigationEvent.emit(1) // Switch to Console tab
            executor.executeCommand(state.binaryPath, state.reviewCommand).collect { line ->
                _uiState.update { it.copy(consoleOutput = it.consoleOutput + line + "\n") }
            }
            
            val outputDir = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
            publisher.publishDirectoryContent(outputDir)
            
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach { it.delete() }
            val outputDir = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
            File(outputDir).listFiles()?.forEach { it.delete() }
            _uiState.update { it.copy(input1 = "", input2 = "", reviewCommand = "") }
        }
    }
}
