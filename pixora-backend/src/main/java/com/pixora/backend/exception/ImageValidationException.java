package com.pixora.backend.exception;

import lombok.Getter;

@Getter
public class ImageValidationException extends RuntimeException {

    private final String errorCode;

    public ImageValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
