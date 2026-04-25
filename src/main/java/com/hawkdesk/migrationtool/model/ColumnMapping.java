package com.hawkdesk.migrationtool.model;

public class ColumnMapping {

    public static final String SKIP = "__SKIP__";
    public static final String AUTO_DETECT = "AUTO";

    private String sourceColumnName;
    private String sourceColumnType;
    private String targetColumnName;
    private String typeOverride;

    public ColumnMapping() {}

    public ColumnMapping(String sourceColumnName, String sourceColumnType,
                         String targetColumnName, String typeOverride) {
        this.sourceColumnName = sourceColumnName;
        this.sourceColumnType = sourceColumnType;
        this.targetColumnName = targetColumnName;
        this.typeOverride = typeOverride;
    }

    public boolean isSkipped() {
        return SKIP.equals(targetColumnName);
    }

    public String getEffectiveTargetType() {
        if (AUTO_DETECT.equals(typeOverride) || typeOverride == null) {
            return sourceColumnType;
        }
        return typeOverride;
    }

    public String getSourceColumnName() { return sourceColumnName; }
    public void setSourceColumnName(String sourceColumnName) { this.sourceColumnName = sourceColumnName; }

    public String getSourceColumnType() { return sourceColumnType; }
    public void setSourceColumnType(String sourceColumnType) { this.sourceColumnType = sourceColumnType; }

    public String getTargetColumnName() { return targetColumnName; }
    public void setTargetColumnName(String targetColumnName) { this.targetColumnName = targetColumnName; }

    public String getTypeOverride() { return typeOverride; }
    public void setTypeOverride(String typeOverride) { this.typeOverride = typeOverride; }
}
