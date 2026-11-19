package com.ugf.yiffur1.domain.dtext

/**
 * Structured DText parser for e621 post descriptions, comments, wiki pages, and notes.
 * Handles block-level structures ([quote], [section=Title], [code], headers h1.-h6., bullet lists)
 * and inline formatting ([b], [i], [u], [s], [spoiler], [color=...], "label":url, bare URLs,
 * [[wiki_tag]], post #1234, pool #123, user #123, {{tag_search}}).
 */
sealed interface DTextBlock {
    data class Paragraph(val spans: List<DTextInline>) : DTextBlock
    data class Heading(val level: Int, val spans: List<DTextInline>) : DTextBlock
    data class Quote(val blocks: List<DTextBlock>) : DTextBlock
    data class Section(val title: String, val expandedByDefault: Boolean, val blocks: List<DTextBlock>) : DTextBlock
    data class CodeBlock(val code: String) : DTextBlock
    data class BulletItem(val depth: Int, val spans: List<DTextInline>) : DTextBlock
}

sealed interface DTextInline {
    data class Text(val text: String) : DTextInline
    data class Styled(
        val children: List<DTextInline>,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
        val colorHex: String? = null
    ) : DTextInline
    data class Spoiler(val children: List<DTextInline>) : DTextInline
    data class Link(val label: String, val target: LinkTarget) : DTextInline
}

sealed interface LinkTarget {
    data class ExternalUrl(val url: String) : LinkTarget
    data class PostRef(val postId: Int) : LinkTarget
    data class PoolRef(val poolId: Int) : LinkTarget
    data class WikiRef(val title: String) : LinkTarget
    data class TagSearch(val tags: String) : LinkTarget
}

object DTextParser {

    private val blockTagRegex = Regex(
        """\[(quote|code|section(?:,expanded)?(?:=[^\]]*)?)\](.*?)\[/(quote|code|section)\]""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    private val headerRegex = Regex("""^h([1-6])\.\s*(.+)$""")
    private val bulletRegex = Regex("""^(\*+)\s+(.+)$""")

    fun parse(rawInput: String): List<DTextBlock> {
        val normalized = rawInput.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        return parseBlocks(normalized, depth = 0)
    }

    private fun parseBlocks(input: String, depth: Int): List<DTextBlock> {
        if (depth > 6) return listOf(DTextBlock.Paragraph(parseInline(input)))

        val blocks = mutableListOf<DTextBlock>()
        var cursor = 0

        // Find outermost block tags cleanly via linear scan so nested quotes/sections work reliably
        while (cursor < input.length) {
            val nextTag = findNextBlockTag(input, cursor)
            if (nextTag == null) {
                parsePlainLines(input.substring(cursor), blocks)
                break
            }

            if (nextTag.start > cursor) {
                parsePlainLines(input.substring(cursor, nextTag.start), blocks)
            }

            val innerContent = input.substring(nextTag.contentStart, nextTag.contentEnd).trim()
            when (nextTag.kind) {
                "quote" -> blocks.add(DTextBlock.Quote(parseBlocks(innerContent, depth + 1)))
                "code" -> blocks.add(DTextBlock.CodeBlock(innerContent))
                "section" -> blocks.add(
                    DTextBlock.Section(
                        title = nextTag.title.ifBlank { "Section" },
                        expandedByDefault = nextTag.expanded,
                        blocks = parseBlocks(innerContent, depth + 1)
                    )
                )
            }
            cursor = nextTag.end
        }

        return blocks
    }

    private data class BlockMatch(
        val kind: String,
        val title: String,
        val expanded: Boolean,
        val start: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val end: Int
    )

    private val openBlockRegex = Regex(
        """\[(quote|code|section)(,expanded)?(?:=([^\]]*))?\]""",
        RegexOption.IGNORE_CASE
    )

    private fun findNextBlockTag(input: String, fromIndex: Int): BlockMatch? {
        val openMatch = openBlockRegex.find(input, fromIndex) ?: return null
        val kind = openMatch.groupValues[1].lowercase()
        val expanded = openMatch.groupValues[2].isNotEmpty()
        val title = openMatch.groupValues[3].trim()

        val closeTag = "[/$kind]"
        var searchFrom = openMatch.range.last + 1
        var nesting = 1

        while (nesting > 0 && searchFrom < input.length) {
            val nextClose = input.indexOf(closeTag, searchFrom, ignoreCase = true)
            if (nextClose == -1) return null

            // Check if another matching opening tag appears before this close tag
            val nestedOpen = if (kind != "code") {
                val candidate = openBlockRegex.find(input, searchFrom)
                if (candidate != null && candidate.range.first < nextClose && candidate.groupValues[1].equals(kind, ignoreCase = true)) {
                    candidate
                } else null
            } else null

            if (nestedOpen != null) {
                nesting++
                searchFrom = nestedOpen.range.last + 1
            } else {
                nesting--
                if (nesting == 0) {
                    return BlockMatch(
                        kind = kind,
                        title = title,
                        expanded = expanded,
                        start = openMatch.range.first,
                        contentStart = openMatch.range.last + 1,
                        contentEnd = nextClose,
                        end = nextClose + closeTag.length
                    )
                }
                searchFrom = nextClose + closeTag.length
            }
        }
        return null
    }

    private fun parsePlainLines(segment: String, out: MutableList<DTextBlock>) {
        val lines = segment.split("\n")
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                val joined = paragraphBuffer.joinToString("\n").trim()
                if (joined.isNotEmpty()) {
                    out.add(DTextBlock.Paragraph(parseInline(joined)))
                }
                paragraphBuffer.clear()
            }
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }
            val headerMatch = headerRegex.matchEntire(trimmed)
            if (headerMatch != null) {
                flushParagraph()
                val level = headerMatch.groupValues[1].toIntOrNull() ?: 4
                out.add(DTextBlock.Heading(level, parseInline(headerMatch.groupValues[2].trim())))
                continue
            }
            val bulletMatch = bulletRegex.matchEntire(trimmed)
            if (bulletMatch != null) {
                flushParagraph()
                val depth = bulletMatch.groupValues[1].length
                out.add(DTextBlock.BulletItem(depth, parseInline(bulletMatch.groupValues[2].trim())))
                continue
            }
            paragraphBuffer.add(rawLine)
        }
        flushParagraph()
    }

    private val inlineRegex = Regex(
        listOf(
            """\[(b|i|u|s|spoiler)\](.*?)\[/\1\]""",
            """\[color=([^\]]+)\](.*?)\[/color\]""",
            """`([^`\n]+)`""",
            """"([^"]+?)":(https?://[^\s<>\]]+|/[^\s<>\]]+)""",
            """\[\[([^\]|]+)(?:\|([^\]]+))?\]\]""",
            """\{\{([^}]+)\}\}""",
            """\bpost\s*#(\d+)\b""",
            """\bpool\s*#(\d+)\b""",
            """\bthumb\s*#(\d+)\b""",
            """(https?://[^\s<>\)\]]+)"""
        ).joinToString("|"),
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun parseInline(input: String, depth: Int = 0): List<DTextInline> {
        if (input.isEmpty()) return emptyList()
        if (depth > 5) return listOf(DTextInline.Text(input))

        val result = mutableListOf<DTextInline>()
        var cursor = 0

        for (match in inlineRegex.findAll(input)) {
            if (match.range.first < cursor) continue
            if (match.range.first > cursor) {
                result.add(DTextInline.Text(input.substring(cursor, match.range.first)))
            }

            val g = match.groupValues
            when {
                // [b|i|u|s|spoiler]...[/...]
                g[1].isNotEmpty() -> {
                    val tag = g[1].lowercase()
                    val inner = parseInline(g[2], depth + 1)
                    if (tag == "spoiler") {
                        result.add(DTextInline.Spoiler(inner))
                    } else {
                        result.add(
                            DTextInline.Styled(
                                children = inner,
                                bold = tag == "b",
                                italic = tag == "i",
                                underline = tag == "u",
                                strike = tag == "s"
                            )
                        )
                    }
                }
                // [color=...]...[/color]
                g[3].isNotEmpty() -> {
                    result.add(
                        DTextInline.Styled(
                            children = parseInline(g[4], depth + 1),
                            colorHex = g[3].trim()
                        )
                    )
                }
                // `inline code`
                g[5].isNotEmpty() -> {
                    result.add(DTextInline.Styled(listOf(DTextInline.Text(g[5])), code = true))
                }
                // "label":url
                g[6].isNotEmpty() -> {
                    val label = g[6]
                    val rawUrl = g[7].trimEnd('.', ',', ';', '!', '?')
                    val target = if (rawUrl.startsWith("/")) {
                        LinkTarget.ExternalUrl("https://e621.net$rawUrl")
                    } else {
                        LinkTarget.ExternalUrl(rawUrl)
                    }
                    result.add(DTextInline.Link(label, target))
                }
                // [[wiki|display]]
                g[8].isNotEmpty() -> {
                    val wikiTitle = g[8].trim()
                    val label = g[9].ifBlank { wikiTitle.replace('_', ' ') }
                    result.add(DTextInline.Link(label, LinkTarget.WikiRef(wikiTitle)))
                }
                // {{tag search}}
                g[10].isNotEmpty() -> {
                    val tags = g[10].trim()
                    result.add(DTextInline.Link(tags, LinkTarget.TagSearch(tags)))
                }
                // post #1234
                g[11].isNotEmpty() -> {
                    val id = g[11].toIntOrNull()
                    if (id != null) {
                        result.add(DTextInline.Link("post #$id", LinkTarget.PostRef(id)))
                    } else {
                        result.add(DTextInline.Text(match.value))
                    }
                }
                // pool #1234
                g[12].isNotEmpty() -> {
                    val id = g[12].toIntOrNull()
                    if (id != null) {
                        result.add(DTextInline.Link("pool #$id", LinkTarget.PoolRef(id)))
                    } else {
                        result.add(DTextInline.Text(match.value))
                    }
                }
                // thumb #1234
                g[13].isNotEmpty() -> {
                    val id = g[13].toIntOrNull()
                    if (id != null) {
                        result.add(DTextInline.Link("post #$id", LinkTarget.PostRef(id)))
                    } else {
                        result.add(DTextInline.Text(match.value))
                    }
                }
                // bare URL
                g[14].isNotEmpty() -> {
                    val url = g[14].trimEnd('.', ',', ';', '!', '?')
                    result.add(DTextInline.Link(url, LinkTarget.ExternalUrl(url)))
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < input.length) {
            result.add(DTextInline.Text(input.substring(cursor)))
        }
        return result
    }
}
