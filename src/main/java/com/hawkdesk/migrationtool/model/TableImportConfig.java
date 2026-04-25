package com.hawkdesk.migrationtool.model;

import java.util.List;

public class TableImportConfig {

    private String sourceTableName;
    private String targetTableName;
    private boolean createNewTable;
    private String createTableStatement;
    private List<ColumnMapping> columnMappings;

    public TableImportConfig() {}

    public String getSourceTableName() { return sourceTableName; }
    public void setSourceTableName(String sourceTableName) { this.sourceTableName = sourceTableName; }

    public String getTargetTableName() { return targetTableName; }
    public void setTargetTableName(String targetTableName) { this.targetTableName = targetTableName; }

    public boolean isCreateNewTable() { return createNewTable; }
    public void setCreateNewTable(boolean createNewTable) { this.createNewTable = createNewTable; }

    public String getCreateTableStatement() { return createTableStatement; }
    public void setCreateTableStatement(String createTableStatement) { this.createTableStatement = createTableStatement; }

    public List<ColumnMapping> getColumnMappings() { return columnMappings; }
    public void setColumnMappings(List<ColumnMapping> columnMappings) { this.columnMappings = columnMappings; }
}
