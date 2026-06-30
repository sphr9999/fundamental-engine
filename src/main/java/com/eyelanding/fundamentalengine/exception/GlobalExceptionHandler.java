package com.eyelanding.fundamentalengine.exception;

import com.eyelanding.fundamentalengine.dto.common.response.DefaultResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<DefaultResponse<Object>> handleBusinessException(BizException ex, WebRequest request) {
        log.error("Business exception code={}, message={}", ex.getCode(), ex.getMessage());
        log.error("Business exception: ", ex);
        DefaultResponse<Object> response = new DefaultResponse<>();
        response.setResponseCode(ex.getCode());
        response.setMessage(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<DefaultResponse<Object>> handleValidateException(MethodArgumentNotValidException ex, WebRequest request) {
        log.error("Validate exception message: {}", ex.getMessage());
        log.debug("Validate exception: ", ex);
        DefaultResponse<Object> response = new DefaultResponse<>();
        response.setResponseCode(String.valueOf(HttpStatus.UNPROCESSABLE_ENTITY.value()));
        StringBuilder message = new StringBuilder();
        Map<String, String> errors = new HashMap<>();
        for (ObjectError error : ex.getAllErrors()) {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            if (errorMessage != null && errorMessage.contains("{fieldName}")) {
                errorMessage = errorMessage.replace("{fieldName}", fieldName);
            }
//            String errMess = error.getM
            errors.put(fieldName, errorMessage);
        }
        response.setMessage(message.toString().trim());
        response.setData(errors);
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<DefaultResponse<Object>> handleUnauthorizedException(UnauthorizedException ex, WebRequest request) {
        log.error("Authorization Error message={}", ex.getMessage());
        log.debug("Authorization Error: ", ex);
        DefaultResponse<Object> response = new DefaultResponse<>();
        response.setResponseCode(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
        response.setMessage(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DefaultException.class)
    public ResponseEntity<DefaultResponse<Object>> handleDefaultException(DefaultException ex, WebRequest request) {
        log.error("Default exception code={}, message={}", ex.getResponseCode(), ex.getMessage());
        log.debug("Default exception: ", ex);
        DefaultResponse<Object> response = new DefaultResponse<>();
        response.setResponseCode(ex.getResponseCode());
        response.setMessage(ex.getMessage());
        response.setData(ex.getData());
        return new ResponseEntity<>(response, ex.getHttpStatusCode());
    }

    // Catch-all exception handler to cover any unhandled exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DefaultResponse<Object>> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: {}", ex.getMessage());
        log.error("Unexpected error: ", ex);
        DefaultResponse<Object> response = new DefaultResponse<>();
        response.setResponseCode(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        response.setMessage("An unexpected error occurred. Please try again later.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
