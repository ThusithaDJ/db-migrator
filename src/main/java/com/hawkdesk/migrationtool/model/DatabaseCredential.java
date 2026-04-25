package com.hawkdesk.migrationtool.model;

import java.util.Base64;
import java.util.UUID;

public class DatabaseCredential {

    private String id;
    private String name;
    private DatabaseType type;
    private String host;
    private int port;
    private String databaseName;
    private String username;
    private String encodedPassword;

    public DatabaseCredential() {
        this.id = UUID.randomUUID().toString();
    }

    public DatabaseCredential(String name, DatabaseType type, String host, int port,
                               String databaseName, String username, String password) {
        this();
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        setPassword(password);
    }

    public String getPassword() {
        if (encodedPassword == null) return "";
        return new String(Base64.getDecoder().decode(encodedPassword));
    }

    public void setPassword(String plainPassword) {
        this.encodedPassword = Base64.getEncoder().encodeToString(
            plainPassword != null ? plainPassword.getBytes() : new byte[0]
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DatabaseType getType() { return type; }
    public void setType(DatabaseType type) { this.type = type; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEncodedPassword() { return encodedPassword; }
    public void setEncodedPassword(String encodedPassword) { this.encodedPassword = encodedPassword; }

    @Override
    public String toString() {
        return name + " (" + (type != null ? type.getDisplayName() : "?") + " — " + host + ")";
    }
}
