package com.telegram.codex.exception;

public class CodexException extends RuntimeException {

    public CodexException(String message) {
        super(message);
    }

    public CodexException(String message, Throwable cause) {
        super(message, cause);
    }
}
