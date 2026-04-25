package com.hawkdesk.migrationtool.service;

public class TypeConversionException extends RuntimeException {

    private final String rawValue;
    private final String targetType;

    public TypeConversionException(String rawValue, String targetType, String reason) {
        super(String.format("Cannot convert '%s' to %s: %s", rawValue, targetType, reason));
        this.rawValue = rawValue;
        this.targetType = targetType;
    }

    public TypeConversionException(String rawValue, String targetType, Throwable cause) {
        super(String.format("Cannot convert '%s' to %s: %s", rawValue, targetType, cause.getMessage()), cause);
        this.rawValue = rawValue;
        this.targetType = targetType;
    }

    public String getRawValue() { return rawValue; }
    public String getTargetType() { return targetType; }
}
