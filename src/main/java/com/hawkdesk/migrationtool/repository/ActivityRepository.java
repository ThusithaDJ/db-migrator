package com.hawkdesk.migrationtool.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hawkdesk.migrationtool.model.ActivityRecord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ActivityRepository {

    private static final int MAX_RECORDS = 5;
    private static final Path ACTIVITY_FILE = Path.of(
        System.getProperty("user.home"), ".hawkdeskmig", "activity.json"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public List<ActivityRecord> loadRecent() {
        File file = ACTIVITY_FILE.toFile();
        if (!file.exists()) return new ArrayList<>();
        try {
            return MAPPER.readValue(file, new TypeReference<List<ActivityRecord>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void record(ActivityRecord activity) {
        List<ActivityRecord> all = loadRecent();
        all.add(0, activity);
        if (all.size() > MAX_RECORDS) {
            all = all.subList(0, MAX_RECORDS);
        }
        try {
            Files.createDirectories(ACTIVITY_FILE.getParent());
            MAPPER.writeValue(ACTIVITY_FILE.toFile(), all);
        } catch (IOException ignored) {}
    }
}
