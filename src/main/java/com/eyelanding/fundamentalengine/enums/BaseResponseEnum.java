package com.eyelanding.fundamentalengine.enums;

public enum BaseResponseEnum {
    SUCCESS("00000", "Successful"),

    PERSISTENCE_NOT_FOUND("40400", "%s record not found with id: %d"),

    ERROR("99999", "The system is busy, please try again later");

    private final String code;
    private final String message;

    public String getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }

    private BaseResponseEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}

