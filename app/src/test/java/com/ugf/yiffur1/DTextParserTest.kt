package com.ugf.yiffur1

import com.ugf.yiffur1.domain.dtext.DTextBlock
import com.ugf.yiffur1.domain.dtext.DTextInline
import com.ugf.yiffur1.domain.dtext.DTextParser
import com.ugf.yiffur1.domain.dtext.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DTextParserTest {

    @Test
    fun `parses quotes, collapsible sections, headings, bullets, and code blocks`() {
        val sample = """
            h4. Artist Notes
            * First [[canine|dog]] item
            [section=Credits]
            Drawn for "Echo":https://e621.net/users/1
            [/section]
            [quote]
            [b]Hello[/b] [spoiler]secret[/spoiler] post #12345 pool #60555
            [/quote]
            [code]val x = 1[/code]
        """.trimIndent()

        val blocks = DTextParser.parse(sample)
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is DTextBlock.Heading)
        assertTrue(blocks[1] is DTextBlock.BulletItem)
        assertTrue(blocks[2] is DTextBlock.Section)
        assertTrue(blocks[3] is DTextBlock.Quote)
        assertTrue(blocks[4] is DTextBlock.CodeBlock)

        val section = blocks[2] as DTextBlock.Section
        assertEquals("Credits", section.title)

        val quote = blocks[3] as DTextBlock.Quote
        val para = quote.blocks.first() as DTextBlock.Paragraph
        assertTrue(para.spans.any { it is DTextInline.Spoiler })
        assertTrue(para.spans.any { it is DTextInline.Link && it.target == LinkTarget.PostRef(12345) })
        assertTrue(para.spans.any { it is DTextInline.Link && it.target == LinkTarget.PoolRef(60555) })
    }
}
