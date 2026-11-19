package com.ugf.yiffur1.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ugf.yiffur1.domain.dtext.DTextBlock
import com.ugf.yiffur1.domain.dtext.DTextInline
import com.ugf.yiffur1.domain.dtext.DTextParser
import com.ugf.yiffur1.domain.dtext.LinkTarget

@Composable
fun DTextView(
    rawText: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    onOpenUrl: (String) -> Unit = {},
    onOpenPost: (Int) -> Unit = {},
    onOpenPool: (Int) -> Unit = {},
    onOpenWiki: (String) -> Unit = {},
    onSearchTag: (String) -> Unit = {}
) {
    val blocks = remember(rawText) { DTextParser.parse(rawText) }
    if (blocks.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            DTextBlockNode(
                block = block,
                textStyle = textStyle,
                onOpenUrl = onOpenUrl,
                onOpenPost = onOpenPost,
                onOpenPool = onOpenPool,
                onOpenWiki = onOpenWiki,
                onSearchTag = onSearchTag
            )
        }
    }
}

@Composable
private fun DTextBlockNode(
    block: DTextBlock,
    textStyle: TextStyle,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenPool: (Int) -> Unit,
    onOpenWiki: (String) -> Unit,
    onSearchTag: (String) -> Unit
) {
    when (block) {
        is DTextBlock.Paragraph -> {
            DTextInlineParagraph(
                spans = block.spans,
                style = textStyle,
                onOpenUrl = onOpenUrl,
                onOpenPost = onOpenPost,
                onOpenPool = onOpenPool,
                onOpenWiki = onOpenWiki,
                onSearchTag = onSearchTag
            )
        }

        is DTextBlock.Heading -> {
            val headingStyle = when (block.level) {
                1, 2 -> MaterialTheme.typography.titleLarge
                3, 4 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            DTextInlineParagraph(
                spans = block.spans,
                style = headingStyle,
                onOpenUrl = onOpenUrl,
                onOpenPost = onOpenPost,
                onOpenPool = onOpenPool,
                onOpenWiki = onOpenWiki,
                onSearchTag = onSearchTag
            )
        }

        is DTextBlock.BulletItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ((block.depth - 1).coerceAtLeast(0) * 14).dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "•",
                    style = textStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                DTextInlineParagraph(
                    spans = block.spans,
                    style = textStyle,
                    onOpenUrl = onOpenUrl,
                    onOpenPost = onOpenPost,
                    onOpenPool = onOpenPool,
                    onOpenWiki = onOpenWiki,
                    onSearchTag = onSearchTag
                )
            }
        }

        is DTextBlock.Quote -> {
            val accent = MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
                    .drawBehind {
                        drawLine(
                            color = accent,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                block.blocks.forEach { inner ->
                    DTextBlockNode(
                        block = inner,
                        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        onOpenUrl = onOpenUrl,
                        onOpenPost = onOpenPost,
                        onOpenPool = onOpenPool,
                        onOpenWiki = onOpenWiki,
                        onSearchTag = onSearchTag
                    )
                }
            }
        }

        is DTextBlock.CodeBlock -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Text(
                    text = block.code,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        is DTextBlock.Section -> {
            var expanded by remember(block) { mutableStateOf(block.expandedByDefault) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = block.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            block.blocks.forEach { inner ->
                                DTextBlockNode(
                                    block = inner,
                                    textStyle = textStyle,
                                    onOpenUrl = onOpenUrl,
                                    onOpenPost = onOpenPost,
                                    onOpenPool = onOpenPool,
                                    onOpenWiki = onOpenWiki,
                                    onSearchTag = onSearchTag
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun DTextInlineParagraph(
    spans: List<DTextInline>,
    style: TextStyle,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenPool: (Int) -> Unit,
    onOpenWiki: (String) -> Unit,
    onSearchTag: (String) -> Unit
) {
    var revealedSpoilers by remember(spans) { mutableStateOf(setOf<Int>()) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val spoilerBg = MaterialTheme.colorScheme.onSurface
    val spoilerTextColor = MaterialTheme.colorScheme.onSurface
    val textColor = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else style.color

    val targets = remember(spans) { mutableListOf<LinkTarget>() }

    val annotated = remember(spans, revealedSpoilers, linkColor, textColor) {
        targets.clear()
        var spoilerIdx = 0
        buildAnnotatedString {
            fun appendNodes(nodes: List<DTextInline>) {
                for (node in nodes) {
                    when (node) {
                        is DTextInline.Text -> append(node.text)
                        is DTextInline.Styled -> {
                            val decorations = buildList {
                                if (node.underline) add(TextDecoration.Underline)
                                if (node.strike) add(TextDecoration.LineThrough)
                            }
                            val parsedColor = node.colorHex?.let { parseDTextColor(it) }
                            val spanStyle = SpanStyle(
                                fontWeight = if (node.bold) FontWeight.Bold else null,
                                fontStyle = if (node.italic) FontStyle.Italic else null,
                                textDecoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else null,
                                fontFamily = if (node.code) FontFamily.Monospace else null,
                                background = if (node.code) codeBg else Color.Unspecified,
                                color = parsedColor ?: Color.Unspecified
                            )
                            pushStyle(spanStyle)
                            appendNodes(node.children)
                            pop()
                        }
                        is DTextInline.Spoiler -> {
                            val currentIdx = spoilerIdx++
                            val revealed = currentIdx in revealedSpoilers
                            pushStringAnnotation("SPOILER", currentIdx.toString())
                            if (revealed) {
                                pushStyle(SpanStyle(background = codeBg.copy(alpha = 0.7f), color = spoilerTextColor))
                                appendNodes(node.children)
                                pop()
                            } else {
                                pushStyle(SpanStyle(background = spoilerBg, color = spoilerBg))
                                append(" ")
                                appendNodes(node.children)
                                append(" ")
                                pop()
                            }
                            pop()
                        }
                        is DTextInline.Link -> {
                            val targetIndex = targets.size
                            targets.add(node.target)
                            pushStringAnnotation("LINK", targetIndex.toString())
                            pushStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            append(node.label)
                            pop()
                            pop()
                        }
                    }
                }
            }
            appendNodes(spans)
        }
    }

    Box {
        ClickableText(
            text = annotated,
            style = style.copy(color = textColor),
            onClick = { offset ->
                annotated.getStringAnnotations("SPOILER", offset, offset).firstOrNull()?.let { ann ->
                    val idx = ann.item.toIntOrNull()
                    if (idx != null) {
                        revealedSpoilers = if (idx in revealedSpoilers) {
                            revealedSpoilers - idx
                        } else {
                            revealedSpoilers + idx
                        }
                    }
                    return@ClickableText
                }
                annotated.getStringAnnotations("LINK", offset, offset).firstOrNull()?.let { ann ->
                    val target = ann.item.toIntOrNull()?.let { targets.getOrNull(it) } ?: return@ClickableText
                    when (target) {
                        is LinkTarget.ExternalUrl -> onOpenUrl(target.url)
                        is LinkTarget.PostRef -> onOpenPost(target.postId)
                        is LinkTarget.PoolRef -> onOpenPool(target.poolId)
                        is LinkTarget.WikiRef -> onOpenWiki(target.title)
                        is LinkTarget.TagSearch -> onSearchTag(target.tags)
                    }
                }
            }
        )
    }
}

private fun parseDTextColor(raw: String): Color? {
    val clean = raw.trim().lowercase()
    return when (clean) {
        "red" -> Color(0xFFFF6B6B)
        "green" -> Color(0xFF3DDC84)
        "blue" -> Color(0xFF4DABFF)
        "yellow", "gold" -> Color(0xFFF2AC08)
        "orange" -> Color(0xFFFF9800)
        "purple", "violet" -> Color(0xFFD65CFF)
        "pink" -> Color(0xFFFF66B2)
        "cyan", "aqua" -> Color(0xFF4DD0E1)
        "white" -> Color.White
        "grey", "gray" -> Color(0xFF9E9E9E)
        else -> {
            val hex = clean.removePrefix("#")
            if (hex.length == 6) {
                hex.toLongOrNull(16)?.let { Color(0xFF000000L or it) }
            } else null
        }
    }
}
