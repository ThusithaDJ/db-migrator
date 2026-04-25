package com.hawkdesk.migrationtool.service;

import com.hawkdesk.migrationtool.model.*;
import com.hawkdesk.migrationtool.util.HdmigFileUtil;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ImportService {

    private final DatabaseService databaseService;
    private final TypeConversionService typeConverter;

    public ImportService(DatabaseService databaseService, TypeConversionService typeConverter) {
        this.databaseService = databaseService;
        this.typeConverter = typeConverter;
    }

    public ImportResult importData(File hdmigFile, Connection targetConn, List<TableImportConfig> configs,
                                   Consumer<String> logCallback,
                                   BiConsumer<String, Double> progressCallback) {
        Map<String, Long> rowsImported = new LinkedHashMap<>();
        List<FailedRow> failedRows = new ArrayList<>();

        try {
            for (TableImportConfig config : configs) {
                if (config.isCreateNewTable()) {
                    executeCreateTable(targetConn, config);
                }

                long imported = importTable(hdmigFile, targetConn, config, failedRows, logCallback, progressCallback);
                rowsImported.put(config.getSourceTableName(), imported);
            }

            return ImportResult.success(rowsImported, failedRows);

        } catch (Exception e) {
            return ImportResult.failure(e.getMessage(), failedRows);
        }
    }

    private void executeCreateTable(Connection conn, TableImportConfig config) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute(config.getCreateTableStatement());
        }
    }

    private long importTable(File hdmigFile, Connection conn, TableImportConfig config,
                              List<FailedRow> failedRows, Consumer<String> log,
                              BiConsumer<String, Double> progress) throws IOException {
        long imported = 0;
        int rowNum = 0;

        List<ColumnMapping> activeMappings = config.getColumnMappings().stream()
            .filter(m -> !m.isSkipped())
            .toList();

        try (InputStream is = HdmigFileUtil.openTableStream(hdmigFile, config.getSourceTableName());
             CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String[] header = reader.readNext();
            if (header == null) return 0;

            Map<String, Integer> headerIndex = buildHeaderIndex(header);
            String[] csvRow;

            while ((csvRow = reader.readNext()) != null) {
                rowNum++;
                try {
                    Map<String, Object> rowData = mapRow(csvRow, headerIndex, activeMappings);
                    databaseService.executeInsert(conn, config.getTargetTableName(), rowData);
                    imported++;

                    if (log != null) log.accept("Imported row " + rowNum + " into " + config.getTargetTableName());

                } catch (Exception e) {
                    String sourceData = Arrays.toString(csvRow);
                    failedRows.add(new FailedRow(config.getSourceTableName(), rowNum, sourceData, e.getMessage()));
                    if (log != null) log.accept("SKIPPED row " + rowNum + ": " + e.getMessage());
                }

                if (progress != null && rowNum % 100 == 0) {
                    progress.accept(config.getSourceTableName(), (double) rowNum);
                }
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSV parse error: " + e.getMessage(), e);
        }

        return imported;
    }

    private Map<String, Integer> buildHeaderIndex(String[] header) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            index.put(header[i], i);
        }
        return index;
    }

    private Map<String, Object> mapRow(String[] csvRow, Map<String, Integer> headerIndex,
                                        List<ColumnMapping> mappings) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (ColumnMapping mapping : mappings) {
            Integer srcIdx = headerIndex.get(mapping.getSourceColumnName());
            if (srcIdx == null || srcIdx >= csvRow.length) continue;

            String rawValue = csvRow[srcIdx];
            Object converted = typeConverter.convert(
                rawValue.isEmpty() ? null : rawValue,
                mapping.getEffectiveTargetType(),
                ColumnMapping.AUTO_DETECT
            );
            row.put(mapping.getTargetColumnName(), converted);
        }
        return row;
    }

    public List<String[]> validateMappings(File hdmigFile, TableImportConfig config, int sampleSize) {
        List<String[]> issues = new ArrayList<>();
        List<ColumnMapping> activeMappings = config.getColumnMappings().stream()
            .filter(m -> !m.isSkipped())
            .toList();

        try (InputStream is = HdmigFileUtil.openTableStream(hdmigFile, config.getSourceTableName());
             CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String[] header = reader.readNext();
            if (header == null) return issues;
            Map<String, Integer> headerIndex = buildHeaderIndex(header);

            String[] csvRow;
            int rowNum = 0;

            while ((csvRow = reader.readNext()) != null && rowNum < sampleSize) {
                rowNum++;
                for (ColumnMapping mapping : activeMappings) {
                    Integer srcIdx = headerIndex.get(mapping.getSourceColumnName());
                    if (srcIdx == null || srcIdx >= csvRow.length) continue;
                    String rawValue = csvRow[srcIdx];
                    if (rawValue == null || rawValue.isEmpty()) continue;

                    try {
                        typeConverter.convert(rawValue, mapping.getEffectiveTargetType(), ColumnMapping.AUTO_DETECT);
                    } catch (TypeConversionException e) {
                        issues.add(new String[]{
                            config.getSourceTableName(),
                            String.valueOf(rowNum),
                            mapping.getSourceColumnName(),
                            e.getMessage()
                        });
                    }
                }
            }
        } catch (Exception e) {
            issues.add(new String[]{"", "0", "", e.getMessage()});
        }

        return issues;
    }
}
