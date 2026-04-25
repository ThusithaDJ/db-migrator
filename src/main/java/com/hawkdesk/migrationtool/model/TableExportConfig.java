package com.hawkdesk.migrationtool.model;

import java.util.List;

public class TableExportConfig {

    private String tableName;
    private List<String> selectedColumns;

    public TableExportConfig() {}

    public TableExportConfig(String tableName, List<String> selectedColumns) {
        this.tableName = tableName;
        this.selectedColumns = selectedColumns;
    }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public List<String> getSelectedColumns() { return selectedColumns; }
    public void setSelectedColumns(List<String> selectedColumns) { this.selectedColumns = selectedColumns; }
}
