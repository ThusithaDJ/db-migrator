package com.hawkdesk.migrationtool.service;

import com.hawkdesk.migrationtool.model.ColumnDefinition;
import com.hawkdesk.migrationtool.model.DatabaseCredential;
import com.hawkdesk.migrationtool.util.ConnectionFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class DatabaseService {

    private String lastError;

    public Connection connect(DatabaseCredential credential) throws SQLException {
        return ConnectionFactory.create(credential);
    }

    public boolean testConnection(DatabaseCredential credential) {
        try (Connection conn = ConnectionFactory.create(credential)) {
            lastError = null;
            return conn.isValid(5);
        } catch (SQLException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public String getLastError() { return lastError; }

    public List<String> getTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    public List<ColumnDefinition> getTableSchema(Connection conn, String tableName) throws SQLException {
        List<ColumnDefinition> columns = new ArrayList<>();
        List<String> primaryKeys = getPrimaryKeys(conn, tableName);

        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                int columnSize = rs.getInt("COLUMN_SIZE");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                boolean pk = primaryKeys.contains(name);

                String sqlType = formatSqlType(typeName, columnSize, rs);
                columns.add(new ColumnDefinition(name, sqlType, nullable, pk));
            }
        }
        return columns;
    }

    private List<String> getPrimaryKeys(Connection conn, String tableName) throws SQLException {
        List<String> keys = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return keys;
    }

    private String formatSqlType(String typeName, int columnSize, ResultSet rs) throws SQLException {
        String upper = typeName.toUpperCase();
        if (upper.contains("CHAR") || upper.contains("TEXT") || upper.contains("BINARY")) {
            if (columnSize > 0 && columnSize < 32768) {
                return upper + "(" + columnSize + ")";
            }
        }
        if (upper.equals("DECIMAL") || upper.equals("NUMERIC")) {
            int scale = rs.getInt("DECIMAL_DIGITS");
            return upper + "(" + columnSize + "," + scale + ")";
        }
        return upper;
    }

    public boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        try (ResultSet rs = meta.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public void createTable(Connection conn, String tableName, List<ColumnDefinition> columns) throws SQLException {
        boolean mssql = isMsSql(conn);
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(quote(tableName, mssql)).append(" (");
        StringJoiner joiner = new StringJoiner(", ");
        List<String> pks = new ArrayList<>();

        for (ColumnDefinition col : columns) {
            StringBuilder colDef = new StringBuilder(quote(col.getName(), mssql))
                .append(" ").append(col.getSqlType());
            if (!col.isNullable()) colDef.append(" NOT NULL");
            joiner.add(colDef.toString());
            if (col.isPrimaryKey()) pks.add(quote(col.getName(), mssql));
        }

        if (!pks.isEmpty()) {
            joiner.add("PRIMARY KEY (" + String.join(", ", pks) + ")");
        }

        sql.append(joiner).append(")");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    public void executeInsert(Connection conn, String tableName, Map<String, Object> rowData) throws SQLException {
        if (rowData.isEmpty()) return;

        boolean mssql = isMsSql(conn);
        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner valJoiner = new StringJoiner(", ");
        rowData.keySet().forEach(k -> {
            colJoiner.add(quote(k, mssql));
            valJoiner.add("?");
        });

        String sql = "INSERT INTO " + quote(tableName, mssql) +
            " (" + colJoiner + ") VALUES (" + valJoiner + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object value : rowData.values()) {
                ps.setObject(idx++, value);
            }
            ps.executeUpdate();
        }
    }

    public long getRowCount(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + quote(tableName, isMsSql(conn));
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public String generateCreateTableSql(String tableName, List<ColumnDefinition> columns) {
        return generateCreateTableSql(tableName, columns, false);
    }

    public String generateCreateTableSql(String tableName, List<ColumnDefinition> columns, boolean mssql) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(quote(tableName, mssql)).append(" (\n");
        StringJoiner joiner = new StringJoiner(",\n  ", "  ", "\n");
        List<String> pks = new ArrayList<>();

        for (ColumnDefinition col : columns) {
            StringBuilder colDef = new StringBuilder(quote(col.getName(), mssql))
                .append(" ").append(col.getSqlType());
            if (!col.isNullable()) colDef.append(" NOT NULL");
            joiner.add(colDef.toString());
            if (col.isPrimaryKey()) pks.add(quote(col.getName(), mssql));
        }

        if (!pks.isEmpty()) {
            joiner.add("PRIMARY KEY (" + String.join(", ", pks) + ")");
        }

        sql.append(joiner).append(")");
        return sql.toString();
    }

    private String quoteIdentifier(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private String quoteIdentifier(Connection conn, String name) throws SQLException {
        return quote(name, isMsSql(conn));
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
}
