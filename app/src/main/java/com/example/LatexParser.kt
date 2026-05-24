package com.example

object LatexParser {

    fun parse(latex: String): List<DocxBlock> {
        val blocks = mutableListOf<DocxBlock>()
        
        // 1. Split into lines or paragraphs
        val paragraphs = latex.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        for (paragraph in paragraphs) {
            val lines = paragraph.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                when {
                    trimmed.startsWith("\\title{") || trimmed.startsWith("\\chapter{") -> {
                        val text = extractContent(trimmed, "\\{", "\\}")
                        blocks.add(DocxBlock(type = "h1", runs = parseRuns(text)))
                    }
                    trimmed.startsWith("\\section{") -> {
                        val text = extractContent(trimmed, "\\section{", "}")
                        blocks.add(DocxBlock(type = "h2", runs = parseRuns(text)))
                    }
                    trimmed.startsWith("\\subsection{") -> {
                        val text = extractContent(trimmed, "\\subsection{", "}")
                        blocks.add(DocxBlock(type = "h3", runs = parseRuns(text)))
                    }
                    trimmed.startsWith("\\item ") -> {
                        val text = trimmed.removePrefix("\\item ").trim()
                        blocks.add(DocxBlock(type = "li", runs = parseRuns(text)))
                    }
                    else -> {
                        blocks.add(DocxBlock(type = "p", runs = parseRuns(trimmed)))
                    }
                }
            }
        }
        
        return blocks
    }

    private fun extractContent(line: String, startTag: String, endTag: String): String {
        val startIndex = line.indexOf(startTag.replace("\\", ""))
        val actualStart = if (startIndex != -1) startIndex + startTag.replace("\\", "").length else {
            val idx = line.indexOf('{')
            if (idx != -1) idx + 1 else 0
        }
        val endIndex = line.lastIndexOf(endTag.replace("\\", ""))
        val actualEnd = if (endIndex != -1) endIndex else line.length
        
        return if (actualStart < actualEnd) {
            line.substring(actualStart, actualEnd)
        } else {
            line
        }
    }
    
    private fun parseRuns(text: String): List<DocxRun> {
        val runs = mutableListOf<DocxRun>()
        var currentText = ""
        var isBold = false
        var isItalic = false
        
        var i = 0
        while (i < text.length) {
            if (text.startsWith("\\textbf{", i)) {
                if (currentText.isNotEmpty()) {
                    runs.add(DocxRun(text = currentText, bold = isBold, italic = isItalic))
                    currentText = ""
                }
                isBold = true
                i += "\\textbf{".length
            } else if (text.startsWith("\\textit{", i)) {
                if (currentText.isNotEmpty()) {
                    runs.add(DocxRun(text = currentText, bold = isBold, italic = isItalic))
                    currentText = ""
                }
                isItalic = true
                i += "\\textit{".length
            } else if (text[i] == '}') {
                if (isBold || isItalic) {
                    if (currentText.isNotEmpty()) {
                        runs.add(DocxRun(text = currentText, bold = isBold, italic = isItalic))
                        currentText = ""
                    }
                    isBold = false
                    isItalic = false
                } else {
                    currentText += text[i]
                }
                i++
            } else if (text[i] == '$') {
                if (currentText.isNotEmpty()) {
                    runs.add(DocxRun(text = currentText, bold = isBold, italic = isItalic))
                    currentText = ""
                }
                i++
                var mathContent = ""
                while (i < text.length && text[i] != '$') {
                    mathContent += text[i]
                    i++
                }
                runs.add(DocxRun(text = mathContent, italic = true))
                if (i < text.length) i++ // skip closing $
            } else {
                currentText += text[i]
                i++
            }
        }
        
        if (currentText.isNotEmpty()) {
            runs.add(DocxRun(text = currentText, bold = isBold, italic = isItalic))
        }
        
        return runs
    }
}
