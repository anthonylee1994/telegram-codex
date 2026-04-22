package com.telegram.codex.util;

public final class HtmlEscaper {

    private HtmlEscaper() {
        // Utility class
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
