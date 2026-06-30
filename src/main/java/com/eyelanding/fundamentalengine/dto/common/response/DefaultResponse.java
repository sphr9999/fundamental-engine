package com.eyelanding.fundamentalengine.dto.common.response;

import com.eyelanding.fundamentalengine.enums.BaseResponseEnum;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.Date;

@Data
public class DefaultResponse<T> {

    private String responseCode;

    private String message;

    private Date timestamp = new Date();

    private T data;

    private static <T> DefaultResponse<T> build() {
        return new DefaultResponse<>();
    }

    public static <T> DefaultResponse<T> ok(T data) {
        DefaultResponse<T> response = build();
        response.setResponseCode(BaseResponseEnum.SUCCESS.getCode());
        response.setMessage(BaseResponseEnum.SUCCESS.getMessage());
        response.setData(data);
        return response;
    }

    public static <T> DefaultResponse<T> ok(T data, String message) {
        DefaultResponse<T> response = ok(data);
        response.setMessage(message);
        return response;
    }

    public static <T> DefaultResponse<T> failed(String code, T data, String message, HttpServletResponse response1) {
        response1.setStatus(HttpStatus.BAD_REQUEST.value());

        DefaultResponse<T> response = build();
        response.setResponseCode(code);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> DefaultResponse<T> failed(String code, T data, String message) {

        DefaultResponse<T> response = build();
        response.setResponseCode(code);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

}
