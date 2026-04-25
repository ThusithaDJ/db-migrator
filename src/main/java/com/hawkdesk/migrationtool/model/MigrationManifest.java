package com.hawkdesk.migrationtool.model;

import java.util.ArrayList;
import java.util.List;

public class MigrationManifest {

    private static final String TOOL_VERSION = "HawkDesk Migration Tool v1.0";

    private String exportedAt;
    private String exportedBy = TOOL_VERSION;
    private List<TableSchema> tables = new ArrayList<>();

    public MigrationManifest() {}

    public String getExportedAt() { return exportedAt; }
    public void setExportedAt(String exportedAt) { this.exportedAt = exportedAt; }

    public String getExportedBy() { return exportedBy; }
    public void setExportedBy(String exportedBy) { this.exportedBy = exportedBy; }

    public List<TableSchema> getTables() { return tables; }
    public void setTables(List<TableSchema> tables) { this.tables = tables; }

    public TableSchema getTable(String tableName) {
        return tables.stream()
            .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
            .findFirst()
            .orElse(null);
    }
}
