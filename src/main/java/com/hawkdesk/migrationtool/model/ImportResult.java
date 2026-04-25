package com.hawkdesk.migrationtool.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportResult {

    private boolean success;
    private Map<String, Long> rowsImported = new LinkedHashMap<>();
    private List<FailedRow> failedRows = new ArrayList<>();
    private String errorMessage;

    public static ImportResult success(Map<String, Long> rowsImported, List<FailedRow> failedRows) {
        ImportResult r = new ImportResult();
        r.success = true;
        r.rowsImported = rowsImported;
        r.failedRows = failedRows;
        return r;
    }

    public static ImportResult failure(String errorMessage, List<FailedRow> failedRows) {
        ImportResult r = new ImportResult();
        r.success = false;
        r.errorMessage = errorMessage;
        r.failedRows = failedRows != null ? failedRows : new ArrayList<>();
        return r;
    }

    public int getTablesImported() { return rowsImported.size(); }
    public long getTotalRowsImported() { return rowsImported.values().stream().mapToLong(Long::longValue).sum(); }
    public int getFailedRowCount() { return failedRows.size(); }

    public boolean isSuccess() { return success; }
    public Map<String, Long> getRowsImported() { return rowsImported; }
    public List<FailedRow> getFailedRows() { return failedRows; }
    public String getErrorMessage() { return errorMessage; }
}
