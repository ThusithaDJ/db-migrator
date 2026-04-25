package com.hawkdesk.migrationtool.model;

public class FailedRow {

    private String tableName;
    private int rowNumber;
    private String sourceData;
    private String errorMessage;

    public FailedRow() {}

    public FailedRow(String tableName, int rowNumber, String sourceData, String errorMessage) {
        this.tableName = tableName;
        this.rowNumber = rowNumber;
        this.sourceData = sourceData;
        this.errorMessage = errorMessage;
    }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public String getSourceData() { return sourceData; }
    public void setSourceData(String sourceData) { this.sourceData = sourceData; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
