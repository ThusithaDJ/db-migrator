package com.hawkdesk.migrationtool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hawkdesk.migrationtool.model.MigrationManifest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class HdmigFileUtil {

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String DATA_PREFIX = "data/";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private HdmigFileUtil() {}

    public static void write(File outputFile, MigrationManifest manifest,
                             Map<String, byte[]> csvContents) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(MAPPER.writeValueAsBytes(manifest));
            zip.closeEntry();

            for (Map.Entry<String, byte[]> entry : csvContents.entrySet()) {
                zip.putNextEntry(new ZipEntry(DATA_PREFIX + entry.getKey() + ".csv"));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    public static MigrationManifest readManifest(File hdmigFile) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(hdmigFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (MANIFEST_ENTRY.equals(entry.getName())) {
                    return MAPPER.readValue(zip.readAllBytes(), MigrationManifest.class);
                }
            }
        }
        throw new IOException("manifest.json not found in " + hdmigFile.getName());
    }

    public static Map<String, byte[]> readTableCsvBytes(File hdmigFile) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(hdmigFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith(DATA_PREFIX) && name.endsWith(".csv")) {
                    String tableName = name.substring(DATA_PREFIX.length(), name.length() - 4);
                    result.put(tableName, zip.readAllBytes());
                }
            }
        }
        return result;
    }

    public static InputStream openTableStream(File hdmigFile, String tableName) throws IOException {
        ZipInputStream zip = new ZipInputStream(new FileInputStream(hdmigFile), StandardCharsets.UTF_8);
        String target = DATA_PREFIX + tableName + ".csv";
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (target.equals(entry.getName())) {
                return zip;
            }
        }
        zip.close();
        throw new IOException("Table data not found: " + tableName);
    }
}
