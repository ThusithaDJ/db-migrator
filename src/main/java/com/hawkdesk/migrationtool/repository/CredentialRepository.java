package com.hawkdesk.migrationtool.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hawkdesk.migrationtool.model.DatabaseCredential;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CredentialRepository {

    private static final Path CREDENTIALS_FILE = Path.of(
        System.getProperty("user.home"), ".hawkdeskmig", "credentials.json"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public List<DatabaseCredential> loadAll() {
        File file = CREDENTIALS_FILE.toFile();
        if (!file.exists()) return new ArrayList<>();
        try {
            return MAPPER.readValue(file, new TypeReference<List<DatabaseCredential>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveAll(List<DatabaseCredential> credentials) throws IOException {
        ensureDirectory();
        MAPPER.writeValue(CREDENTIALS_FILE.toFile(), credentials);
    }

    public void add(DatabaseCredential credential) throws IOException {
        List<DatabaseCredential> all = loadAll();
        all.removeIf(c -> c.getId().equals(credential.getId()));
        all.add(credential);
        saveAll(all);
    }

    public void delete(String id) throws IOException {
        List<DatabaseCredential> all = loadAll();
        all.removeIf(c -> c.getId().equals(id));
        saveAll(all);
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(CREDENTIALS_FILE.getParent());
    }
}
