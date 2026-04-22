package com.telegram.codex.exception;

public class RasterizationException extends DocumentProcessingException {

    public RasterizationException(String message) {
        super(message);
    }

    public RasterizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
