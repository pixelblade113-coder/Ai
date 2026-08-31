package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBlockBackground
import com.example.ui.theme.PrimaryCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Header(val text: String, val level: Int) : MarkdownBlock()
    data class Code(val code: String, val language: String = "") : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<Pair<Int, String>>) : MarkdownBlock()
    data class BlockQuote(val text: String) : MarkdownBlock()
}

fun parseMarkdown(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 1. Code block
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            // Skip closing ```
            if (i < lines.size && lines[i].trimStart().startsWith("```")) {
                i++
            }
            blocks.add(MarkdownBlock.Code(code = codeLines.joinToString("\n"), language = lang))
            continue
        }

        // 2. Headers
        if (line.startsWith("### ")) {
            blocks.add(MarkdownBlock.Header(line.removePrefix("### ").trim(), 3))
            i++
            continue
        } else if (line.startsWith("## ")) {
            blocks.add(MarkdownBlock.Header(line.removePrefix("## ").trim(), 2))
            i++
            continue
        } else if (line.startsWith("# ")) {
            blocks.add(MarkdownBlock.Header(line.removePrefix("# ").trim(), 1))
            i++
            continue
        }

        // 3. Blockquote
        if (line.startsWith("> ")) {
            val quoteLines = mutableListOf<String>()
            quoteLines.add(line.removePrefix("> ").trim())
            i++
            while (i < lines.size && lines[i].startsWith("> ")) {
                quoteLines.add(lines[i].removePrefix("> ").trim())
                i++
            }
            blocks.add(MarkdownBlock.BlockQuote(quoteLines.joinToString("\n")))
            continue
        }

        // 4. Bullet lists (* or - or •)
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") || line.trimStart().startsWith("• ")) {
            val items = mutableListOf<String>()
            val bulletPrefix = when {
                line.trimStart().startsWith("- ") -> "- "
                line.trimStart().startsWith("* ") -> "* "
                else -> "• "
            }
            items.add(line.trimStart().removePrefix(bulletPrefix).trim())
            i++
            while (i < lines.size && (lines[i].trimStart().startsWith("- ") || lines[i].trimStart().startsWith("* ") || lines[i].trimStart().startsWith("• "))) {
                val currentPrefix = when {
                    lines[i].trimStart().startsWith("- ") -> "- "
                    lines[i].trimStart().startsWith("* ") -> "* "
                    else -> "• "
                }
                items.add(lines[i].trimStart().removePrefix(currentPrefix).trim())
                i++
            }
            blocks.add(MarkdownBlock.BulletList(items))
            continue
        }

        // 5. Numbered lists (1. , 2. )
        val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(line.trimStart())
        if (numberedMatch != null) {
            val items = mutableListOf<Pair<Int, String>>()
            val num = numberedMatch.groupValues[1].toIntOrNull() ?: 1
            val text = numberedMatch.groupValues[2]
            items.add(Pair(num, text))
            i++
            while (i < lines.size) {
                val nextMatch = Regex("^(\\d+)\\.\\s+(.*)").find(lines[i].trimStart())
                if (nextMatch != null) {
                    val n = nextMatch.groupValues[1].toIntOrNull() ?: (items.last().first + 1)
                    items.add(Pair(n, nextMatch.groupValues[2]))
                    i++
                } else {
                    break
                }
            }
            blocks.add(MarkdownBlock.NumberedList(items))
            continue
        }

        // 6. Regular paragraph or empty line
        if (line.isBlank()) {
            i++
            continue
        }

        // Accumulate contiguous paragraph lines
        val paragraphLines = mutableListOf<String>()
        paragraphLines.add(line)
        i++
        while (i < lines.size &&
            lines[i].isNotBlank() &&
            !lines[i].trimStart().startsWith("```") &&
            !lines[i].startsWith("#") &&
            !lines[i].startsWith("> ") &&
            !lines[i].trimStart().startsWith("- ") &&
            !lines[i].trimStart().startsWith("* ") &&
            !lines[i].trimStart().startsWith("• ") &&
            !Regex("^(\\d+)\\.\\s+").containsMatchIn(lines[i].trimStart())
        ) {
            paragraphLines.add(lines[i])
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
    }

    return blocks
}

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = buildFormattedAnnotatedString(block.text, textColor),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        lineHeight = (fontSize.value * 1.3f).sp
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildFormattedAnnotatedString(block.text, textColor),
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                }

                is MarkdownBlock.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(PrimaryCyan, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildFormattedAnnotatedString(block.text, textColor.copy(alpha = 0.85f)),
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = textColor.copy(alpha = 0.85f),
                            lineHeight = 20.sp
                        )
                    }
                }

                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    color = PrimaryCyan,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = buildFormattedAnnotatedString(item, textColor),
                                    fontSize = 15.sp,
                                    color = textColor,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { (num, item) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "$num.",
                                    color = PrimaryCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = buildFormattedAnnotatedString(item, textColor),
                                    fontSize = 15.sp,
                                    color = textColor,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Code -> {
                    CodeBlockView(code = block.code, language = block.language)
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(code: String, language: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBlockBackground)
            .border(1.dp, Color(0xFF2E384D), RoundedCornerShape(8.dp))
    ) {
        Column {
            // Header bar with language name and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131B2E))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language.isNotBlank()) language.uppercase() else "CODE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryCyan,
                    letterSpacing = 0.5.sp
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_code_button")
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) Color(0xFF10B981) else Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Code content with horizontal scroll for long lines
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Parses bold (**text**), italic (*text*), and inline code (`code`) into an AnnotatedString.
 */
fun buildFormattedAnnotatedString(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)|(`([^`]+)`)")
        val matches = regex.findAll(text)

        for (match in matches) {
            val range = match.range
            if (cursor < range.first) {
                withStyle(SpanStyle(color = defaultColor)) {
                    append(text.substring(cursor, range.first))
                }
            }

            val fullMatch = match.value
            when {
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val inner = fullMatch.removeSurrounding("**")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(inner)
                    }
                }
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val inner = fullMatch.removeSurrounding("`")
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF26334A),
                            color = PrimaryCyan,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $inner ")
                    }
                }
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val inner = fullMatch.removeSurrounding("*")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                        append(inner)
                    }
                }
                else -> {
                    append(fullMatch)
                }
            }
            cursor = range.last + 1
        }

        if (cursor < text.length) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(text.substring(cursor))
            }
        }
    }
}
