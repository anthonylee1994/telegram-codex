package com.telegram.codex.exception;

public class MissingDependencyException extends DocumentProcessingException {

    private final String dependencyName;

    public MissingDependencyException(String dependencyName, String message) {
        super(message);
        this.dependencyName = dependencyName;
    }

    public String getDependencyName() {
        return dependencyName;
    }
}
