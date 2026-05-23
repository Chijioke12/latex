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
                val prompt = """
                    You are a LaTeX to MS Word structural converter. Convert the given LaTeX content into a structured JSON representation of the document flow. 
                    ONLY output a raw JSON array matching this schema structure, do not include any markdown formatting like ```json.
                    
                    Schema:
                    [
                      { 
                        "type": "p" | "h1" | "h2" | "h3" | "li" | "equation", 
                        "level": 0, 
                        "runs": [ 
                           { "text": "string", "bold": false, "italic": false } 
                        ] 
                      }
                    ]
                    
                    Rules:
                    1. For math equations, translate them into formatted Unicode text (e.g. superscripts, fractions, greek letters) as best as you can so it reads well in plain text.
                    2. For type 'li', level 0 is top level, 1 is nested.
                    3. Do not include markdown codeblocks in your response, just the raw array.
                    
                    LaTeX Content:
                    $text
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        temperature = 0.2f,
                        responseFormat = ResponseFormat(
                            text = ResponseFormatText(mimeType = "application/json")
                        )
                    )
                )

                val apiKey = BuildConfig.GEMINI_API_KEY
                
                val jsonResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
                
                if (jsonResponse != null) {
                    lastGeneratedJson = jsonResponse
                    _state.value = ConversionState.AwaitingSaveLocation
                } else {
                    _state.value = ConversionState.Error("Received empty response from converter.")
                }

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
    
    fun resetState() {
        _state.value = ConversionState.Idle
    }
}
