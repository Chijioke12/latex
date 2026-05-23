package com.example

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.OutputStream

@Serializable
data class DocxBlock(
    val type: String,
    val level: Int = 0,
    val runs: List<DocxRun> = emptyList()
)

@Serializable
data class DocxRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false
)

object DocxGenerator {

    fun createDocxFromBlocks(context: Context, uri: Uri, blocksJson: String) {
        val blocks = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<DocxBlock>>(blocksJson)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback if parsing fails
            listOf(DocxBlock(type = "p", runs = listOf(DocxRun(text = "Failed to parse content: $blocksJson"))))
        }

        val document = XWPFDocument()

        for (block in blocks) {
            val paragraph = document.createParagraph()

            when (block.type) {
                "h1" -> {
                    paragraph.style = "Heading1"
                    paragraph.alignment = ParagraphAlignment.CENTER
                }
                "h2" -> {
                    paragraph.style = "Heading2"
                }
                "h3" -> {
                    paragraph.style = "Heading3"
                }
                "li" -> {
                    paragraph.style = "ListParagraph"
                }
                "equation" -> {
                    paragraph.alignment = ParagraphAlignment.CENTER
                }
                else -> {
                    // standard paragraph (p)
                }
            }

            for (runInfo in block.runs) {
                val run = paragraph.createRun()
                run.setText(runInfo.text.replace("\n", ""))
                if (runInfo.bold) {
                    run.isBold = true
                }
                if (runInfo.italic) {
                    run.isItalic = true
                }
                
                when (block.type) {
                    "h1" -> { run.fontSize = 20; run.isBold = true }
                    "h2" -> { run.fontSize = 16; run.isBold = true }
                    "h3" -> { run.fontSize = 14; run.isBold = true }
                }
            }
        }

        // Write to output stream
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            document.write(outputStream)
        }
        document.close()
    }
}
