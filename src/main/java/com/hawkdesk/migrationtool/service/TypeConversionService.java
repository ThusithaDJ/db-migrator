package com.hawkdesk.migrationtool.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

public class TypeConversionService {

    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "y", "on");
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "n", "off");

    private static final List<DateTimeFormatter> DATETIME_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    public Object convert(String rawValue, String targetSqlType, String strategy) throws TypeConversionException {
        if (rawValue == null || rawValue.isEmpty()) {
            return null;
        }

        String baseType = extractBaseType(targetSqlType).toUpperCase();

        return switch (baseType) {
            case "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT" -> convertToInt(rawValue, targetSqlType);
            case "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL" -> convertToDecimal(rawValue, targetSqlType);
            case "DATETIME", "TIMESTAMP", "DATE" -> convertToDateTime(rawValue, targetSqlType);
            case "BOOLEAN", "BIT" -> convertToBoolean(rawValue, targetSqlType);
            default -> rawValue;
        };
    }

    private Long convertToInt(String value, String targetType) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new TypeConversionException(value, targetType, e);
        }
    }

    private BigDecimal convertToDecimal(String value, String targetType) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new TypeConversionException(value, targetType, e);
        }
    }

    private Timestamp convertToDateTime(String value, String targetType) {
        String trimmed = value.trim();
        for (DateTimeFormatter fmt : DATETIME_FORMATS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(trimmed, fmt);
                return Timestamp.valueOf(ldt);
            } catch (DateTimeParseException ignored) {}

            try {
                LocalDate ld = LocalDate.parse(trimmed, fmt);
                return Timestamp.valueOf(ld.atStartOfDay());
            } catch (DateTimeParseException ignored) {}
        }
        throw new TypeConversionException(value, targetType, "no matching date format found");
    }

    private Boolean convertToBoolean(String value, String targetType) {
        String lower = value.trim().toLowerCase();
        if (TRUE_VALUES.contains(lower)) return true;
        if (FALSE_VALUES.contains(lower)) return false;
        throw new TypeConversionException(value, targetType, "expected true/false/1/0/yes/no");
    }

    private String extractBaseType(String sqlType) {
        if (sqlType == null) return "";
        int paren = sqlType.indexOf('(');
        return paren == -1 ? sqlType.trim() : sqlType.substring(0, paren).trim();
    }

    public boolean isTypeMismatch(String sourceType, String targetType) {
        String src = extractBaseType(sourceType).toUpperCase();
        String tgt = extractBaseType(targetType).toUpperCase();
        if (src.equals(tgt)) return false;

        Set<String> numeric = Set.of("INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
            "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL");
        Set<String> text = Set.of("VARCHAR", "CHAR", "TEXT", "NVARCHAR", "NCHAR", "NTEXT",
            "LONGTEXT", "MEDIUMTEXT", "TINYTEXT");
        Set<String> temporal = Set.of("DATE", "DATETIME", "TIMESTAMP", "TIME");

        return !(inSameGroup(src, tgt, numeric) ||
                 inSameGroup(src, tgt, text) ||
                 inSameGroup(src, tgt, temporal));
    }

    private boolean inSameGroup(String a, String b, Set<String> group) {
        return group.contains(a) && group.contains(b);
    }
}
