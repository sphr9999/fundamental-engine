package com.eyelanding.fundamentalengine.exception;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class DefaultException extends RuntimeException {


    private final String responseCode;

    private final Object data;

    private final HttpStatus httpStatusCode;

    public DefaultException(String message, String responseCode, Object data, HttpStatus httpStatusCode) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.responseCode = responseCode;
        this.data = data;
    }

    public DefaultException(String message, String responseCode, Object data) {
        super(message);
        this.httpStatusCode = null;
        this.responseCode = responseCode;
        this.data = data;
    }

    public DefaultException(String message) {
        super(message);
        this.httpStatusCode = null;
        this.responseCode = null;
        this.data = null;
    }

}

