package com.telegram.codex.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
}
