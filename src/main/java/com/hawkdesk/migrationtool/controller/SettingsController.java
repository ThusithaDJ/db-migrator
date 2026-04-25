package com.hawkdesk.migrationtool.controller;

import com.hawkdesk.migrationtool.model.DatabaseCredential;
import com.hawkdesk.migrationtool.model.DatabaseType;
import com.hawkdesk.migrationtool.repository.CredentialRepository;
import com.hawkdesk.migrationtool.service.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.List;

public class SettingsController {

    @FXML private ListView<DatabaseCredential> connectionList;
    @FXML private TextField fieldName;
    @FXML private ComboBox<DatabaseType> fieldType;
    @FXML private TextField fieldHost;
    @FXML private TextField fieldPort;
    @FXML private TextField fieldDatabase;
    @FXML private TextField fieldUsername;
    @FXML private PasswordField fieldPassword;
    @FXML private Label statusLabel;
    @FXML private Button btnSave;
    @FXML private Button btnDelete;
    @FXML private Label passwordWarningLabel;

    private final CredentialRepository credentialRepository = new CredentialRepository();
    private final DatabaseService databaseService = new DatabaseService();
    private DatabaseCredential currentCredential;

    @FXML
    public void initialize() {
        fieldType.getItems().addAll(DatabaseType.values());
        fieldType.getSelectionModel().selectFirst();
        fieldType.setOnAction(e -> updateDefaultPort());

        connectionList.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, selected) -> populateForm(selected)
        );

        passwordWarningLabel.setText("⚠ Passwords are Base64-encoded, not encrypted.");
        btnDelete.setDisable(true);
        loadConnections();
        clearForm();
    }

    private void loadConnections() {
        connectionList.getItems().clear();
        connectionList.getItems().addAll(credentialRepository.loadAll());
    }

    private void populateForm(DatabaseCredential cred) {
        if (cred == null) { clearForm(); return; }
        currentCredential = cred;
        fieldName.setText(cred.getName());
        fieldType.setValue(cred.getType());
        fieldHost.setText(cred.getHost());
        fieldPort.setText(String.valueOf(cred.getPort()));
        fieldDatabase.setText(cred.getDatabaseName());
        fieldUsername.setText(cred.getUsername());
        fieldPassword.setText(cred.getPassword());
        statusLabel.setText("");
        btnDelete.setDisable(false);
    }

    @FXML
    private void onNewConnection() {
        currentCredential = new DatabaseCredential();
        connectionList.getSelectionModel().clearSelection();
        clearForm();
        btnDelete.setDisable(true);
        fieldName.requestFocus();
    }

    @FXML
    private void onTestConnection() {
        DatabaseCredential cred = buildCredentialFromForm();
        if (cred == null) return;

        setStatus("Testing connection...", false);
        new Thread(() -> {
            boolean ok = databaseService.testConnection(cred);
            Platform.runLater(() -> {
                if (ok) {
                    setStatus("Connection successful!", false);
                } else {
                    setStatus("Failed: " + databaseService.getLastError(), true);
                }
            });
        }).start();
    }

    @FXML
    private void onSave() {
        DatabaseCredential cred = buildCredentialFromForm();
        if (cred == null) return;

        try {
            credentialRepository.add(cred);
            currentCredential = cred;
            loadConnections();
            selectCredential(cred.getId());
            setStatus("Saved successfully.", false);
        } catch (IOException e) {
            setStatus("Save failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onDelete() {
        if (currentCredential == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete connection \"" + currentCredential.getName() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.YES) {
                try {
                    credentialRepository.delete(currentCredential.getId());
                    currentCredential = null;
                    loadConnections();
                    clearForm();
                    btnDelete.setDisable(true);
                } catch (IOException e) {
                    setStatus("Delete failed: " + e.getMessage(), true);
                }
            }
        });
    }

    private DatabaseCredential buildCredentialFromForm() {
        String name = fieldName.getText().trim();
        String host = fieldHost.getText().trim();
        String database = fieldDatabase.getText().trim();
        String username = fieldUsername.getText().trim();

        if (name.isEmpty() || host.isEmpty() || database.isEmpty() || username.isEmpty()) {
            setStatus("Please fill in all required fields.", true);
            return null;
        }

        int port;
        try {
            port = Integer.parseInt(fieldPort.getText().trim());
        } catch (NumberFormatException e) {
            setStatus("Port must be a valid number.", true);
            return null;
        }

        DatabaseCredential cred = currentCredential != null ? currentCredential : new DatabaseCredential();
        cred.setName(name);
        cred.setType(fieldType.getValue());
        cred.setHost(host);
        cred.setPort(port);
        cred.setDatabaseName(database);
        cred.setUsername(username);
        cred.setPassword(fieldPassword.getText());
        return cred;
    }

    private void updateDefaultPort() {
        DatabaseType type = fieldType.getValue();
        if (type == DatabaseType.MYSQL && fieldPort.getText().isEmpty()) {
            fieldPort.setText("3306");
        } else if (type == DatabaseType.MSSQL && fieldPort.getText().isEmpty()) {
            fieldPort.setText("1433");
        }
    }

    private void clearForm() {
        fieldName.clear();
        fieldType.getSelectionModel().selectFirst();
        fieldHost.clear();
        fieldPort.clear();
        fieldDatabase.clear();
        fieldUsername.clear();
        fieldPassword.clear();
        statusLabel.setText("");
        currentCredential = null;
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setTextFill(isError ? Color.RED : Color.GREEN);
    }

    private void selectCredential(String id) {
        connectionList.getItems().stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .ifPresent(c -> connectionList.getSelectionModel().select(c));
    }

    public List<DatabaseCredential> getSavedCredentials() {
        return credentialRepository.loadAll();
    }
}
