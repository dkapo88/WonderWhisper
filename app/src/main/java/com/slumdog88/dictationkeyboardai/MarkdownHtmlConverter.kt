package com.slumdog88.dictationkeyboardai

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Utility class to convert Markdown text to HTML for rich clipboard copying
 */
object MarkdownHtmlConverter {
    
    private val options by lazy {
        MutableDataSet().apply {
            // Add extensions for better compatibility
            set(Parser.EXTENSIONS, listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create()
            ))
            
            // Configure HTML output for better app compatibility
            set(HtmlRenderer.SOFT_BREAK, "<br />")
            set(HtmlRenderer.HARD_BREAK, "<br />")
        }
    }
    
    private val parser by lazy { Parser.builder(options).build() }
    private val renderer by lazy { HtmlRenderer.builder(options).build() }
    
    /**
     * Converts Markdown text to HTML
     */
    fun markdownToHtml(markdown: String): String {
        return try {
            val document = parser.parse(markdown)
            val html = renderer.render(document)
            
            // Wrap in basic HTML structure for better app compatibility
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; }
                    h1, h2, h3, h4, h5, h6 { margin-top: 1em; margin-bottom: 0.5em; }
                    p { margin: 0.5em 0; }
                    ul, ol { margin: 0.5em 0; padding-left: 2em; }
                    blockquote { margin: 1em 0; padding-left: 1em; border-left: 3px solid #ccc; }
                    code { background: #f5f5f5; padding: 0.2em 0.4em; border-radius: 3px; }
                    pre { background: #f5f5f5; padding: 1em; border-radius: 5px; overflow-x: auto; }
                </style>
            </head>
            <body>
            $html
            </body>
            </html>
            """.trimIndent()
        } catch (e: Exception) {
            // Fallback to simple HTML escaping
            markdown.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>")
        }
    }
} 