package com.hawkdesk.migrationtool.controller;

import com.hawkdesk.migrationtool.model.ActivityRecord;
import com.hawkdesk.migrationtool.repository.ActivityRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {

    @FXML private TableView<ActivityRow> activityTable;
    @FXML private TableColumn<ActivityRow, String> colType;
    @FXML private TableColumn<ActivityRow, String> colTimestamp;
    @FXML private TableColumn<ActivityRow, String> colStatus;
    @FXML private TableColumn<ActivityRow, String> colDescription;
    @FXML private Label noActivityLabel;

    private MainController mainController;
    private final ActivityRepository activityRepository = new ActivityRepository();
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colTimestamp.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        refreshActivity();
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void onExport() {
        mainController.openExportWizard();
    }

    @FXML
    private void onImport() {
        mainController.openImportWizard();
    }

    public void refreshActivity() {
        List<ActivityRecord> records = activityRepository.loadRecent();
        activityTable.getItems().clear();

        if (records.isEmpty()) {
            activityTable.setVisible(false);
            noActivityLabel.setVisible(true);
        } else {
            activityTable.setVisible(true);
            noActivityLabel.setVisible(false);
            for (ActivityRecord r : records) {
                activityTable.getItems().add(new ActivityRow(
                    r.getType().name(),
                    r.getTimestamp() != null ? r.getTimestamp().format(DISPLAY_FMT) : "-",
                    r.getStatus(),
                    r.getDescription()
                ));
            }
        }
    }

    public static class ActivityRow {
        private final String type;
        private final String timestamp;
        private final String status;
        private final String description;

        public ActivityRow(String type, String timestamp, String status, String description) {
            this.type = type;
            this.timestamp = timestamp;
            this.status = status;
            this.description = description;
        }

        public String getType() { return type; }
        public String getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
        public String getDescription() { return description; }
    }
}
