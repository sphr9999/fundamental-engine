package com.eyelanding.fundamentalengine.exception;


import com.eyelanding.fundamentalengine.enums.BaseResponseEnum;

public class BizException extends RuntimeException {

    private final String code;

    public BizException(BaseResponseEnum responseEnum, String message) {
        super(message);
        this.code = responseEnum.getCode();
    }

    public BizException(BaseResponseEnum responseEnum) {
        super(responseEnum.getMessage());
        this.code = responseEnum.getCode();
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
