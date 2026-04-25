package com.hawkdesk.migrationtool.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {

    @FXML private TabPane tabPane;
    @FXML private Tab dashboardTab;
    @FXML private Tab settingsTab;

    private DashboardController dashboardController;

    @FXML
    public void initialize() {
        loadDashboard();
        loadSettings();
    }

    private void loadDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            dashboardTab.setContent(loader.load());
            dashboardController = loader.getController();
            dashboardController.setMainController(this);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dashboard", e);
        }
    }

    private void loadSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            settingsTab.setContent(loader.load());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load settings", e);
        }
    }

    public void openExportWizard() {
        openWizardWindow("/fxml/export_wizard.fxml", "Export Data — HawkDesk Migration Tool");
    }

    public void openImportWizard() {
        openWizardWindow("/fxml/import_wizard.fxml", "Import Data — HawkDesk Migration Tool");
    }

    private void openWizardWindow(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 860, 620);
            scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setMinWidth(700);
            stage.setMinHeight(520);

            stage.setOnHidden(e -> {
                if (dashboardController != null) {
                    dashboardController.refreshActivity();
                }
            });

            stage.showAndWait();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open " + title, e);
        }
    }

    public void switchToSettings() {
        tabPane.getSelectionModel().select(settingsTab);
    }
}
