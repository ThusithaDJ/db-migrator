package com.hawkdesk.migrationtool.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ColumnDefinition {

    private String name;
    private String sqlType;
    private boolean nullable;
    @JsonProperty("isPrimaryKey")
    private boolean isPrimaryKey;

    public ColumnDefinition() {}

    public ColumnDefinition(String name, String sqlType, boolean nullable, boolean isPrimaryKey) {
        this.name = name;
        this.sqlType = sqlType;
        this.nullable = nullable;
        this.isPrimaryKey = isPrimaryKey;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSqlType() { return sqlType; }
    public void setSqlType(String sqlType) { this.sqlType = sqlType; }

    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }

    public boolean isPrimaryKey() { return isPrimaryKey; }
    public void setPrimaryKey(boolean primaryKey) { isPrimaryKey = primaryKey; }

    @Override
    public String toString() {
        return name + " (" + sqlType + ")";
    }
}
