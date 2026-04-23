package com.telegram.codex.integration.telegram.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegramMessageFormatterTest {

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter(null);

    @Test
    void convertsFencedCodeBlocksToTelegramHtml() {
        String formatted = formatter.formatForTelegram("""
            可以，幫你加返註解：

            ```python
            # 印出一段文字
            print('Hello from Python')

            # 計算 1 到 5 嘅總和，再印出結果
            print(sum(range(1, 6)))
            ```
            """);

        assertEquals("""
            可以，幫你加返註解：

            <pre><code># 印出一段文字
            print('Hello from Python')

            # 計算 1 到 5 嘅總和，再印出結果
            print(sum(range(1, 6)))
            </code></pre>
            """, formatted);
    }

    @Test
    void escapesHtmlOutsideAndInsideCodeBlocks() {
        String formatted = formatter.formatForTelegram("""
            <b>bold</b>
            ```js
            if (a < b && c > d) {
              console.log("x");
            }
            ```
            """);

        assertEquals("""
            &lt;b&gt;bold&lt;/b&gt;
            <pre><code>if (a &lt; b &amp;&amp; c &gt; d) {
              console.log("x");
            }
            </code></pre>
            """, formatted);
    }

    @Test
    void convertsInlineCodeOutsideCodeBlocks() {
        String formatted = formatter.formatForTelegram("""
            用 `print('Hello from Python')` 同 `print(sum(range(1, 6)))` 做例子。
            """);

        assertEquals("""
            用 <code>print('Hello from Python')</code> 同 <code>print(sum(range(1, 6)))</code> 做例子。
            """, formatted);
    }

    @Test
    void doesNotParseInlineCodeInsideFencedCodeBlocks() {
        String formatted = formatter.formatForTelegram("""
            ```python
            print(`raw`)
            ```
            外面先有 `inline`
            """);

        assertEquals("""
            <pre><code>print(`raw`)
            </code></pre>
            外面先有 <code>inline</code>
            """, formatted);
    }

    @Test
    void convertsBoldOutsideCode() {
        String formatted = formatter.formatForTelegram("""
            Telegram reply **74.3 億股** 都未處理好
            """);

        assertEquals("""
            Telegram reply <b>74.3 億股</b> 都未處理好
            """, formatted);
    }

    @Test
    void doesNotParseBoldInsideInlineCodeOrFencedCodeBlocks() {
        String formatted = formatter.formatForTelegram("""
            `**literal**`
            ```text
            **block**
            ```
            外面 **bold**
            """);

        assertEquals("""
            <code>**literal**</code>
            <pre><code>**block**
            </code></pre>
            外面 <b>bold</b>
            """, formatted);
    }

    @Test
    void convertsItalicOutsideCode() {
        String formatted = formatter.formatForTelegram("""
            呢段有 *重點*、_補充_ 同 __強調__
            """);

        assertEquals("""
            呢段有 *重點*、_補充_ 同 <i>強調</i>
            """, formatted);
    }

    @Test
    void doesNotParseItalicInsideInlineCodeOrFencedCodeBlocks() {
        String formatted = formatter.formatForTelegram("""
            `__literal__`
            ```text
            __block__
            ```
            外面 **bold** 同 __italic__
            """);

        assertEquals("""
            <code>__literal__</code>
            <pre><code>__block__
            </code></pre>
            外面 <b>bold</b> 同 <i>italic</i>
            """, formatted);
    }

    @Test
    void doesNotConvertNumericOnlyItalicMarkers() {
        String formatted = formatter.formatForTelegram("""
            呢啲保留原樣：*123*、_123_，但 __123__ 要轉
        """);

        assertEquals("""
            呢啲保留原樣：*123*、_123_，但 <i>123</i> 要轉
        """, formatted);
    }

    @Test
    void convertsNumericBoldMarkers() {
        String formatted = formatter.formatForTelegram("""
            呢個要轉：**123**
            """);

        assertEquals("""
            呢個要轉：<b>123</b>
            """, formatted);
    }

    @Test
    void convertsStrikethroughOutsideCode() {
        String formatted = formatter.formatForTelegram("""
            呢段有 ~~刪除線~~
            """);

        assertEquals("""
            呢段有 <s>刪除線</s>
            """, formatted);
    }

    @Test
    void doesNotParseStrikethroughInsideInlineCodeOrFencedCodeBlocks() {
        String formatted = formatter.formatForTelegram("""
            `~~literal~~`
            ```text
            ~~block~~
            ```
            外面 ~~strike~~
            """);

        assertEquals("""
            <code>~~literal~~</code>
            <pre><code>~~block~~
            </code></pre>
            外面 <s>strike</s>
            """, formatted);
    }

    @Test
    void convertsSpoilerOutsideCode() {
        String formatted = formatter.formatForTelegram("""
            呢段有 ||劇透||
            """);

        assertEquals("""
            呢段有 <tg-spoiler>劇透</tg-spoiler>
            """, formatted);
    }

    @Test
    void doesNotParseSpoilerInsideInlineCodeOrFencedCodeBlocks() {
        String formatted = formatter.formatForTelegram("""
            `||literal||`
            ```text
            ||block||
            ```
            外面 ||spoiler||
            """);

        assertEquals("""
            <code>||literal||</code>
            <pre><code>||block||
            </code></pre>
            外面 <tg-spoiler>spoiler</tg-spoiler>
            """, formatted);
    }
}
