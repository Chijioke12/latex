package com.example

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ConversionState {
    object Idle : ConversionState()
    object Converting : ConversionState()
    data class PreviewReady(val blocks: List<DocxBlock>) : ConversionState()
    object AwaitingSaveLocation : ConversionState()
    data class Success(val message: String) : ConversionState()
    data class Error(val message: String) : ConversionState()
}

class ConverterViewModel : ViewModel() {

    private val _state = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    private var lastGeneratedJson: String? = null

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun startConversion() {
        val text = _inputText.value
        if (text.isBlank()) {
            _state.value = ConversionState.Error("Please enter LaTeX content.")
            return
        }

        _state.value = ConversionState.Converting

        viewModelScope.launch {
            try {
                val parsedBlocks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    LatexParser.parse(text)
                }
                
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
                val jsonResponse = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DocxBlock.serializer()), parsedBlocks)
                lastGeneratedJson = jsonResponse

                _state.value = ConversionState.PreviewReady(parsedBlocks)
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = ConversionState.Error("Failed to convert: ${e.message}")
            }
        }
    }

    fun saveDocument(context: Context, uri: Uri) {
        val json = lastGeneratedJson
        if (json == null) {
            _state.value = ConversionState.Error("No generated content to save.")
            return
        }
        
        _state.value = ConversionState.Converting
        
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DocxGenerator.createDocxFromBlocks(context, uri, json)
                }
                _state.value = ConversionState.Success("Document saved successfully!")
                lastGeneratedJson = null
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = ConversionState.Error("Failed to save document: ${e.message}")
            }
        }
    }
    
    fun requestSave() {
        if (lastGeneratedJson != null) {
            _state.value = ConversionState.AwaitingSaveLocation
        }
    }
    
    fun resetState() {
        _state.value = ConversionState.Idle
    }
}
