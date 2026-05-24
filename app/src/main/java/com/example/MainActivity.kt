package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ConverterViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val defaultExample = "\\title{Example LaTeX}\n\\section{Introduction}\nThis is a simple \\textbf{equation}: \$E=mc^2\$."
        viewModel.updateInputText(defaultExample)

        setContent {
            MyApplicationTheme {
                val state by viewModel.state.collectAsState()
                val inputText by viewModel.inputText.collectAsState()
                val context = LocalContext.current

                val saveDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                ) { uri: Uri? ->
                    if (uri != null) {
                        viewModel.saveDocument(context, uri)
                    } else {
                        viewModel.resetState()
                        Toast.makeText(context, "Save cancelled", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(state) {
                    if (state is ConversionState.AwaitingSaveLocation) {
                        saveDocumentLauncher.launch("converted_document.docx")
                    } else if (state is ConversionState.Success) {
                        Toast.makeText(context, (state as ConversionState.Success).message, Toast.LENGTH_LONG).show()
                        viewModel.resetState()
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("LaTeX to DocX Converter") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (state is ConversionState.Error) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(
                                        text = (state as ConversionState.Error).message,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        if (state is ConversionState.PreviewReady) {
                            val blocks = (state as ConversionState.PreviewReady).blocks
                            DocumentPreview(
                                blocks = blocks,
                                modifier = Modifier.weight(1f),
                                onSaveClick = { viewModel.requestSave() },
                                onBackClick = { viewModel.resetState() }
                            )
                        } else {
                            Text("Enter LaTeX Code:", style = MaterialTheme.typography.titleMedium)
    
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { viewModel.updateInputText(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                placeholder = { Text("\\section{Hello World}\n...") },
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
    
                            Button(
                                onClick = { viewModel.startConversion() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state !is ConversionState.Converting && state !is ConversionState.AwaitingSaveLocation
                            ) {
                                if (state is ConversionState.Converting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Converting...")
                                } else {
                                    Text("Preview Document")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentPreview(
    blocks: List<DocxBlock>,
    modifier: Modifier = Modifier,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Document Preview", style = MaterialTheme.typography.titleLarge)
        
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 4.dp
        ) {
            LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(blocks) { block ->
                    val annotatedString = buildAnnotatedString {
                        block.runs.forEach { run ->
                            val style = SpanStyle(
                                fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal
                            )
                            withStyle(style) {
                                append(run.text)
                            }
                        }
                    }

                    when (block.type) {
                        "h1" -> Text(annotatedString, style = MaterialTheme.typography.headlineMedium)
                        "h2" -> Text(annotatedString, style = MaterialTheme.typography.headlineSmall)
                        "h3" -> Text(annotatedString, style = MaterialTheme.typography.titleLarge)
                        "li" -> Row {
                            Text("• ", style = MaterialTheme.typography.bodyLarge)
                            Text(annotatedString, style = MaterialTheme.typography.bodyLarge)
                        }
                        "equation" -> Text(
                            annotatedString, 
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Text(annotatedString, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Edit Input")
            }
            Button(
                onClick = onSaveClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save as DocX")
            }
        }
    }
}
