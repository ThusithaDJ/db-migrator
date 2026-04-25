package com.hawkdesk.migrationtool.service;

import com.hawkdesk.migrationtool.model.*;
import com.hawkdesk.migrationtool.util.HdmigFileUtil;
import com.opencsv.CSVWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

public class ExportService {

    public ExportResult export(Connection conn, List<TableExportConfig> configs, File outputFile,
                               BiConsumer<String, Double> progressCallback) {
        Map<String, Long> rowsExported = new LinkedHashMap<>();
        Map<String, byte[]> csvContents = new LinkedHashMap<>();
        MigrationManifest manifest = new MigrationManifest();
        manifest.setExportedAt(Instant.now().toString());

        try {
            for (TableExportConfig config : configs) {
                if (progressCallback != null) {
                    progressCallback.accept(config.getTableName(), -1.0);
                }
                ExportedTable exported = exportTable(conn, config);
                csvContents.put(config.getTableName(), exported.csvBytes);
                manifest.getTables().add(exported.schema);
                rowsExported.put(config.getTableName(), exported.rowCount);

                if (progressCallback != null) {
                    progressCallback.accept(config.getTableName(), 1.0);
                }
            }

            HdmigFileUtil.write(outputFile, manifest, csvContents);
            return ExportResult.success(outputFile, rowsExported);

        } catch (Exception e) {
            return ExportResult.failure(e.getMessage());
        }
    }

    private ExportedTable exportTable(Connection conn, TableExportConfig config) throws SQLException, IOException {
        boolean mssql = isMsSql(conn);
        String columns = String.join(", ", config.getSelectedColumns().stream()
            .map(c -> quote(c, mssql)).toList());
        String sql = "SELECT " + columns + " FROM " + quote(config.getTableName(), mssql);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TableSchema schema = new TableSchema(config.getTableName());
        long rowCount = 0;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter writer = new CSVWriter(osw)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            String[] header = new String[colCount];
            for (int i = 1; i <= colCount; i++) {
                header[i - 1] = meta.getColumnName(i);
                schema.getColumns().add(new ColumnDefinition(
                    meta.getColumnName(i),
                    meta.getColumnTypeName(i),
                    meta.isNullable(i) == ResultSetMetaData.columnNullable,
                    false
                ));
            }
            writer.writeNext(header);

            while (rs.next()) {
                String[] row = new String[colCount];
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    row[i - 1] = val != null ? val.toString() : "";
                }
                writer.writeNext(row);
                rowCount++;
            }
        }

        schema.setRowCount(rowCount);
        return new ExportedTable(schema, baos.toByteArray(), rowCount);
    }

    private String quote(String name, boolean mssql) {
        if (mssql) return "[" + name.replace("]", "]]") + "]";
        return "`" + name.replace("`", "``") + "`";
    }

    private boolean isMsSql(Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("microsoft");
        } catch (SQLException e) {
            return false;
        }
    }

    private record ExportedTable(TableSchema schema, byte[] csvBytes, long rowCount) {}
}
