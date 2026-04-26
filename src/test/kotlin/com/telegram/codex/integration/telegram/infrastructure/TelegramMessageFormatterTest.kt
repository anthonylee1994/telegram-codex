package com.telegram.codex.integration.telegram.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelegramMessageFormatterTest {
    private val formatter = TelegramMessageFormatter(null)

    @Test
    fun convertsFencedCodeBlocksToTelegramHtml() {
        val formatted = formatter.formatForTelegram(
            """
            可以，幫你加返註解：

            ```python
            # 印出一段文字
            print('Hello from Python')
            ```
            """.trimIndent(),
        )

        assertEquals(
            """
            可以，幫你加返註解：

            <pre><code># 印出一段文字
            print('Hello from Python')
            </code></pre>
            """.trimIndent(),
            formatted,
        )
    }

    @Test
    fun escapesHtmlOutsideAndInsideCodeBlocks() {
        val formatted = formatter.formatForTelegram(
            """
            <b>bold</b>
            ```js
            if (a < b && c > d) {
              console.log("x");
            }
            ```
            """.trimIndent(),
        )

        assertEquals(
            """
            &lt;b&gt;bold&lt;/b&gt;
            <pre><code>if (a &lt; b &amp;&amp; c &gt; d) {
              console.log("x");
            }
            </code></pre>
            """.trimIndent(),
            formatted,
        )
    }

    @Test
    fun convertsInlineFormattingOutsideCode() {
        assertEquals("用 <code>x</code> 同 <b>bold</b>", formatter.formatForTelegram("用 `x` 同 **bold**"))
        assertEquals("外面 <s>strike</s> 同 <tg-spoiler>spoiler</tg-spoiler>", formatter.formatForTelegram("外面 ~~strike~~ 同 ||spoiler||"))
        assertEquals("呢段有 *重點*、_補充_ 同 <i>強調</i>", formatter.formatForTelegram("呢段有 *重點*、_補充_ 同 __強調__"))
    }
}
