package com.hawkdesk.migrationtool.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class ActivityRecord {

    public enum ActivityType { EXPORT, IMPORT }

    private ActivityType type;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private String status;
    private String description;

    public ActivityRecord() {}

    public ActivityRecord(ActivityType type, LocalDateTime timestamp, String status, String description) {
        this.type = type;
        this.timestamp = timestamp;
        this.status = status;
        this.description = description;
    }

    public ActivityType getType() { return type; }
    public void setType(ActivityType type) { this.type = type; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
