package com.hawkdesk.migrationtool.model;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExportResult {

    private boolean success;
    private File outputFile;
    private Map<String, Long> rowsExported = new LinkedHashMap<>();
    private String errorMessage;

    public static ExportResult success(File outputFile, Map<String, Long> rowsExported) {
        ExportResult r = new ExportResult();
        r.success = true;
        r.outputFile = outputFile;
        r.rowsExported = rowsExported;
        return r;
    }

    public static ExportResult failure(String errorMessage) {
        ExportResult r = new ExportResult();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    public int getTablesExported() { return rowsExported.size(); }
    public long getTotalRowsExported() { return rowsExported.values().stream().mapToLong(Long::longValue).sum(); }

    public boolean isSuccess() { return success; }
    public File getOutputFile() { return outputFile; }
    public Map<String, Long> getRowsExported() { return rowsExported; }
    public String getErrorMessage() { return errorMessage; }
}
