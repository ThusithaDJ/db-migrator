package com.hawkdesk.migrationtool.controller;

import com.hawkdesk.migrationtool.model.*;
import com.hawkdesk.migrationtool.repository.ActivityRepository;
import com.hawkdesk.migrationtool.repository.CredentialRepository;
import com.hawkdesk.migrationtool.service.DatabaseService;
import com.hawkdesk.migrationtool.service.ExportService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ExportWizardController {

    @FXML private StackPane stepContainer;
    @FXML private Button btnBack;
    @FXML private Button btnNext;
    @FXML private Label lblStep;
    @FXML private HBox stepIndicator;

    private int currentStep = 0;
    private final int TOTAL_STEPS = 5;

    private final DatabaseService databaseService = new DatabaseService();
    private final ExportService exportService = new ExportService();
    private final CredentialRepository credentialRepository = new CredentialRepository();
    private final ActivityRepository activityRepository = new ActivityRepository();

    // Step state
    private DatabaseCredential selectedCredential;
    private Connection activeConnection;
    private List<String> selectedTables = new ArrayList<>();
    private Map<String, List<String>> selectedColumns = new LinkedHashMap<>();
    private Map<String, List<ColumnDefinition>> tableSchemas = new LinkedHashMap<>();

    // Step nodes (built once, shown/hidden)
    private Node[] stepNodes;

    // Step 2 widgets
    private ComboBox<DatabaseCredential> credentialCombo;
    private Label step2StatusLabel;
    private Button step2TestBtn;

    // Step 3 widgets
    private ListView<String> tableListView;
    private final Set<String> checkedTables = new LinkedHashSet<>();

    // Step 4 widgets
    private Accordion columnAccordion;

    // Step 5 widgets
    private Label step5SummaryLabel;
    private Button btnChooseOutput;
    private Button btnExport;
    private Label step5StatusLabel;
    private VBox progressContainer;
    private File outputFile;

    @FXML
    public void initialize() {
        buildSteps();
        showStep(0);
    }

    private void buildSteps() {
        stepNodes = new Node[TOTAL_STEPS];
        stepNodes[0] = buildStep1();
        stepNodes[1] = buildStep2();
        stepNodes[2] = buildStep3();
        stepNodes[3] = buildStep4Placeholder();
        stepNodes[4] = buildStep5();

        for (Node n : stepNodes) {
            stepContainer.getChildren().add(n);
            n.setVisible(false);
        }
    }

    private Node buildStep1() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");
        box.getChildren().addAll(
            label("Export Format", "step-title"),
            label("Select the format for your migration file.", "step-subtitle"),
            new Separator(),
            label("Format:", null),
            buildFormatCombo(),
            label("The .hdmig format is a ZIP archive containing a manifest and CSV data files.\n" +
                  "It can be imported into any compatible HawkDeskPOS installation.", "description-label")
        );
        return box;
    }

    private ComboBox<String> buildFormatCombo() {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().add(".hdmig — HawkDesk Migration Format");
        combo.getSelectionModel().selectFirst();
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private Node buildStep2() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");

        credentialCombo = new ComboBox<>();
        credentialCombo.setMaxWidth(Double.MAX_VALUE);
        credentialCombo.setPromptText("Select a saved connection…");
        credentialCombo.getItems().addAll(credentialRepository.loadAll());

        step2StatusLabel = new Label();
        step2StatusLabel.setWrapText(true);

        step2TestBtn = new Button("Test Connection");
        step2TestBtn.setOnAction(e -> testStep2Connection());

        box.getChildren().addAll(
            label("Connect to Source Database", "step-title"),
            label("Choose a saved database connection.", "step-subtitle"),
            new Separator(),
            label("Saved Connection:", null),
            credentialCombo,
            step2TestBtn,
            step2StatusLabel
        );
        return box;
    }

    private void testStep2Connection() {
        DatabaseCredential cred = credentialCombo.getValue();
        if (cred == null) {
            showStep2Status("Please select a connection first.", true);
            return;
        }
        showStep2Status("Testing…", false);
        new Thread(() -> {
            boolean ok = databaseService.testConnection(cred);
            Platform.runLater(() -> {
                if (ok) {
                    selectedCredential = cred;
                    showStep2Status("Connected successfully.", false);
                } else {
                    selectedCredential = null;
                    showStep2Status("Failed: " + databaseService.getLastError(), true);
                }
            });
        }).start();
    }

    private void showStep2Status(String msg, boolean error) {
        step2StatusLabel.setText(msg);
        step2StatusLabel.setTextFill(error ? Color.RED : Color.GREEN);
    }

    private Node buildStep3() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");

        tableListView = new ListView<>();
        tableListView.setPrefHeight(280);
        tableListView.setCellFactory(lv -> new CheckBoxListCell());
        VBox.setVgrow(tableListView, Priority.ALWAYS);

        HBox buttons = new HBox(8,
            createTextButton("Select All", () -> {
                checkedTables.addAll(tableListView.getItems());
                tableListView.refresh();
            }),
            createTextButton("Deselect All", () -> {
                checkedTables.clear();
                tableListView.refresh();
            })
        );

        box.getChildren().addAll(
            label("Select Tables", "step-title"),
            label("Choose which tables to export.", "step-subtitle"),
            new Separator(),
            buttons,
            tableListView
        );
        return box;
    }

    private class CheckBoxListCell extends ListCell<String> {
        private final CheckBox checkBox = new CheckBox();

        CheckBoxListCell() {
            checkBox.setOnAction(e -> {
                if (checkBox.isSelected()) checkedTables.add(getItem());
                else checkedTables.remove(getItem());
            });
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                checkBox.setText(item);
                checkBox.setSelected(checkedTables.contains(item));
                setGraphic(checkBox);
                setText(null);
            }
        }
    }

    private Node buildStep4Placeholder() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");
        columnAccordion = new Accordion();
        ScrollPane scroll = new ScrollPane(columnAccordion);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        box.getChildren().addAll(
            label("Select Columns", "step-title"),
            label("Choose which columns to include for each table.", "step-subtitle"),
            new Separator(),
            scroll
        );
        return box;
    }

    private Node buildStep5() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");

        step5SummaryLabel = new Label();
        step5SummaryLabel.setWrapText(true);

        btnChooseOutput = new Button("Choose Output Location…");
        btnChooseOutput.setOnAction(e -> chooseOutputFile());

        btnExport = new Button("Export");
        btnExport.setDefaultButton(true);
        btnExport.setDisable(true);
        btnExport.setOnAction(e -> runExport());

        step5StatusLabel = new Label();
        step5StatusLabel.setWrapText(true);

        progressContainer = new VBox(8);

        box.getChildren().addAll(
            label("Export", "step-title"),
            label("Review your selection and choose an output location.", "step-subtitle"),
            new Separator(),
            step5SummaryLabel,
            new HBox(8, btnChooseOutput, btnExport),
            step5StatusLabel,
            progressContainer
        );
        return box;
    }

    private void chooseOutputFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Migration File");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("HawkDesk Migration Files", "*.hdmig"));
        File chosen = chooser.showSaveDialog(getStage());
        if (chosen != null) {
            if (!chosen.getName().endsWith(".hdmig")) {
                chosen = new File(chosen.getAbsolutePath() + ".hdmig");
            }
            outputFile = chosen;
            btnExport.setDisable(false);
            step5StatusLabel.setText("Output: " + outputFile.getAbsolutePath());
            step5StatusLabel.setTextFill(Color.GRAY);
        }
    }

    private void runExport() {
        btnExport.setDisable(true);
        btnChooseOutput.setDisable(true);
        progressContainer.getChildren().clear();

        List<TableExportConfig> configs = buildExportConfigs();
        Map<String, ProgressBar> progressBars = new LinkedHashMap<>();
        for (TableExportConfig cfg : configs) {
            Label lbl = new Label(cfg.getTableName());
            ProgressBar pb = new ProgressBar(0);
            pb.setPrefWidth(400);
            progressBars.put(cfg.getTableName(), pb);
            progressContainer.getChildren().add(new VBox(4, lbl, pb));
        }

        Task<ExportResult> task = new Task<>() {
            @Override
            protected ExportResult call() {
                try (Connection conn = databaseService.connect(selectedCredential)) {
                    return exportService.export(conn, configs, outputFile, (table, progress) ->
                        Platform.runLater(() -> {
                            ProgressBar pb = progressBars.get(table);
                            if (pb != null) pb.setProgress(progress < 0 ? ProgressBar.INDETERMINATE_PROGRESS : progress);
                        })
                    );
                } catch (SQLException e) {
                    return ExportResult.failure(e.getMessage());
                }
            }
        };

        task.setOnSucceeded(e -> {
            ExportResult result = task.getValue();
            if (result.isSuccess()) {
                step5StatusLabel.setText("Export complete! " + result.getTotalRowsExported() + " rows exported to: " + outputFile.getName());
                step5StatusLabel.setTextFill(Color.GREEN);
                progressBars.values().forEach(pb -> pb.setProgress(1.0));
                activityRepository.record(new ActivityRecord(
                    ActivityRecord.ActivityType.EXPORT,
                    LocalDateTime.now(), "Success",
                    result.getTablesExported() + " tables, " + result.getTotalRowsExported() + " rows → " + outputFile.getName()
                ));
                Button openFolder = new Button("Open Containing Folder");
                openFolder.setOnAction(ev -> openFolder(outputFile));
                progressContainer.getChildren().add(openFolder);
            } else {
                step5StatusLabel.setText("Export failed: " + result.getErrorMessage());
                step5StatusLabel.setTextFill(Color.RED);
                activityRepository.record(new ActivityRecord(
                    ActivityRecord.ActivityType.EXPORT,
                    LocalDateTime.now(), "Failed", result.getErrorMessage()
                ));
            }
            btnChooseOutput.setDisable(false);
        });

        task.setOnFailed(e -> {
            step5StatusLabel.setText("Unexpected error: " + task.getException().getMessage());
            step5StatusLabel.setTextFill(Color.RED);
            btnChooseOutput.setDisable(false);
        });

        new Thread(task).start();
    }

    private List<TableExportConfig> buildExportConfigs() {
        return selectedTables.stream()
            .map(t -> new TableExportConfig(t, selectedColumns.getOrDefault(t, List.of())))
            .filter(c -> !c.getSelectedColumns().isEmpty())
            .collect(Collectors.toList());
    }

    private void openFolder(File file) {
        try {
            Desktop.getDesktop().open(file.getParentFile());
        } catch (IOException ignored) {}
    }

    @FXML
    private void onBack() {
        showStep(currentStep - 1);
    }

    @FXML
    private void onNext() {
        if (!validateStep(currentStep)) return;
        if (currentStep == TOTAL_STEPS - 1) return;
        prepareStep(currentStep + 1);
        showStep(currentStep + 1);
    }

    private boolean validateStep(int step) {
        return switch (step) {
            case 0 -> true;
            case 1 -> {
                if (selectedCredential == null) {
                    showStep2Status("Please test the connection first.", true);
                    yield false;
                }
                yield true;
            }
            case 2 -> {
                selectedTables = new ArrayList<>(checkedTables);
                yield !selectedTables.isEmpty();
            }
            case 3 -> {
                collectColumnSelections();
                yield selectedColumns.values().stream().noneMatch(List::isEmpty);
            }
            default -> true;
        };
    }

    private void collectColumnSelections() {
        selectedColumns.clear();
        for (TitledPane pane : columnAccordion.getPanes()) {
            String tableName = pane.getText();
            VBox content = (VBox) pane.getContent();
            List<String> cols = content.getChildren().stream()
                .filter(n -> n instanceof CheckBox)
                .map(n -> (CheckBox) n)
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .collect(Collectors.toList());
            selectedColumns.put(tableName, cols);
        }
    }

    private void prepareStep(int step) {
        switch (step) {
            case 2 -> loadTableList();
            case 3 -> buildColumnAccordion();
            case 4 -> buildStep5Summary();
        }
    }

    private void loadTableList() {
        tableListView.getItems().clear();
        checkedTables.clear();
        if (selectedCredential == null) return;
        new Thread(() -> {
            try (Connection conn = databaseService.connect(selectedCredential)) {
                List<String> tables = databaseService.getTables(conn);
                Platform.runLater(() -> {
                    tableListView.getItems().addAll(tables);
                    checkedTables.addAll(tables); // all checked by default
                    tableListView.refresh();
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showStep2Status("Could not load tables: " + e.getMessage(), true));
            }
        }).start();
    }

    private void buildColumnAccordion() {
        columnAccordion.getPanes().clear();
        tableSchemas.clear();

        for (String table : selectedTables) {
            TitledPane pane = new TitledPane();
            pane.setText(table);
            VBox content = new VBox(6);
            content.setPadding(new Insets(8));
            pane.setContent(content);
            columnAccordion.getPanes().add(pane);

            new Thread(() -> {
                try (Connection conn = databaseService.connect(selectedCredential)) {
                    List<ColumnDefinition> cols = databaseService.getTableSchema(conn, table);
                    tableSchemas.put(table, cols);
                    Platform.runLater(() -> {
                        cols.forEach(col -> {
                            CheckBox cb = new CheckBox(col.getName());
                            cb.setSelected(true);
                            Tooltip.install(cb, new Tooltip(col.getSqlType() + (col.isNullable() ? " (nullable)" : " NOT NULL")));
                            content.getChildren().add(cb);
                        });
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> content.getChildren().add(new Label("Error: " + e.getMessage())));
                }
            }).start();
        }

        if (!columnAccordion.getPanes().isEmpty()) {
            columnAccordion.setExpandedPane(columnAccordion.getPanes().get(0));
        }
    }

    private void buildStep5Summary() {
        collectColumnSelections();
        StringBuilder sb = new StringBuilder();
        long totalRows = 0;
        for (String table : selectedTables) {
            int colCount = selectedColumns.getOrDefault(table, List.of()).size();
            sb.append("• ").append(table).append(" — ").append(colCount).append(" column(s)\n");
        }
        step5SummaryLabel.setText(sb.toString().trim());
        outputFile = null;
        btnExport.setDisable(true);
        step5StatusLabel.setText("");
        progressContainer.getChildren().clear();
    }

    private void showStep(int step) {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            stepNodes[i].setVisible(i == step);
        }
        currentStep = step;
        lblStep.setText("Step " + (step + 1) + " of " + TOTAL_STEPS);
        btnBack.setDisable(step == 0);
        btnNext.setText(step == TOTAL_STEPS - 1 ? "Finish" : "Next >");
        btnNext.setDisable(step == TOTAL_STEPS - 1);
        updateStepIndicator();
    }

    private void updateStepIndicator() {
        stepIndicator.getChildren().forEach(n -> {
            n.getStyleClass().removeAll("step-dot-active", "step-dot-done");
            int idx = stepIndicator.getChildren().indexOf(n);
            if (idx == currentStep) n.getStyleClass().add("step-dot-active");
            else if (idx < currentStep) n.getStyleClass().add("step-dot-done");
        });
    }

    private Label label(String text, String styleClass) {
        Label lbl = new Label(text);
        if (styleClass != null) lbl.getStyleClass().add(styleClass);
        return lbl;
    }

    private Button createTextButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Stage getStage() {
        return (Stage) stepContainer.getScene().getWindow();
    }
}
