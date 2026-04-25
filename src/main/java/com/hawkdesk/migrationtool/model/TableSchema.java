package com.hawkdesk.migrationtool.model;

import java.util.ArrayList;
import java.util.List;

public class TableSchema {

    private String tableName;
    private long rowCount;
    private List<ColumnDefinition> columns = new ArrayList<>();

    public TableSchema() {}

    public TableSchema(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public long getRowCount() { return rowCount; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }

    public List<ColumnDefinition> getColumns() { return columns; }
    public void setColumns(List<ColumnDefinition> columns) { this.columns = columns; }

    @Override
    public String toString() { return tableName; }
}
