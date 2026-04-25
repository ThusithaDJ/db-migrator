package com.hawkdesk.migrationtool.model;

public enum DatabaseType {
    MYSQL("MySQL"),
    MSSQL("MS SQL Server");

    private final String displayName;

    DatabaseType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
