package com.clipador.media;

public class ExternalProcessException extends RuntimeException {
    private final String errorCode;

    public ExternalProcessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ExternalProcessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}

