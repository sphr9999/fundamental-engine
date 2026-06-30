package com.eyelanding.fundamentalengine.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(){
        super(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    }
}

