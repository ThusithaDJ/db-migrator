package com.hawkdesk.migrationtool.util;

import com.hawkdesk.migrationtool.model.DatabaseCredential;
import com.hawkdesk.migrationtool.model.DatabaseType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {

    private ConnectionFactory() {}

    public static Connection create(DatabaseCredential credential) throws SQLException {
        String url = buildUrl(credential);
        return DriverManager.getConnection(url, credential.getUsername(), credential.getPassword());
    }

    private static String buildUrl(DatabaseCredential credential) {
        if (credential.getType() == DatabaseType.MYSQL) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                credential.getHost(), credential.getPort(), credential.getDatabaseName());
        } else {
            return String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
                credential.getHost(), credential.getPort(), credential.getDatabaseName());
        }
    }
}
