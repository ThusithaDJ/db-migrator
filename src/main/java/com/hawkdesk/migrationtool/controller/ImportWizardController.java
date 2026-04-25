package com.hawkdesk.migrationtool.controller;

import com.hawkdesk.migrationtool.model.*;
import com.hawkdesk.migrationtool.repository.ActivityRepository;
import com.hawkdesk.migrationtool.repository.CredentialRepository;
import com.hawkdesk.migrationtool.service.DatabaseService;
import com.hawkdesk.migrationtool.service.ImportService;
import com.hawkdesk.migrationtool.service.TypeConversionService;
import com.hawkdesk.migrationtool.util.HdmigFileUtil;
import com.opencsv.CSVWriter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ImportWizardController {

    @FXML private StackPane stepContainer;
    @FXML private Button btnBack;
    @FXML private Button btnNext;
    @FXML private Label lblStep;
    @FXML private HBox stepIndicator;

    private int currentStep = 0;
    private final int TOTAL_STEPS = 5;

    private final DatabaseService databaseService = new DatabaseService();
    private final TypeConversionService typeConverter = new TypeConversionService();
    private final ImportService importService = new ImportService(databaseService, typeConverter);
    private final CredentialRepository credentialRepository = new CredentialRepository();
    private final ActivityRepository activityRepository = new ActivityRepository();

    private File hdmigFile;
    private MigrationManifest manifest;
    private DatabaseCredential selectedCredential;
    private Connection activeConnection;
    private List<String> targetTables = new ArrayList<>();

    // Step nodes
    private Node[] stepNodes;

    // Step 1
    private Label step1FilePath;
    private TableView<TableInfoRow> step1ManifestTable;

    // Step 2
    private ComboBox<DatabaseCredential> credentialCombo;
    private Label step2Status;

    // Step 3
    private VBox tableSelectionBox;
    private Map<String, CheckBox> tableCheckBoxes = new LinkedHashMap<>();
    private Map<String, ComboBox<String>> targetTableCombos = new LinkedHashMap<>();
    private Map<String, CheckBox> createNewCheckBoxes = new LinkedHashMap<>();
    private Map<String, TextArea> createSqlAreas = new LinkedHashMap<>();

    // Step 4
    private Accordion mappingAccordion;
    private Map<String, List<MappingRow>> tableMappingRows = new LinkedHashMap<>();
    private Label step4ValidationLabel;

    // Step 5
    private TextArea step5Log;
    private VBox step5Progress;
    private Label step5Summary;
    private Button btnStartImport;
    private Button btnExportErrors;
    private ImportResult lastImportResult;

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
        stepNodes[3] = buildStep4();
        stepNodes[4] = buildStep5();

        for (Node n : stepNodes) {
            stepContainer.getChildren().add(n);
            n.setVisible(false);
        }
    }

    // ---- Step 1: Select source file ----

    private Node buildStep1() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");

        step1FilePath = new Label("No file selected.");
        step1FilePath.setWrapText(true);

        Button btnBrowse = new Button("Browse…");
        btnBrowse.setOnAction(e -> browseHdmigFile());

        step1ManifestTable = buildManifestPreviewTable();

        box.getChildren().addAll(
            label("Select Source File", "step-title"),
            label("Choose a .hdmig migration file to import.", "step-subtitle"),
            new Separator(),
            new HBox(8, btnBrowse, step1FilePath),
            label("File Contents:", null),
            step1ManifestTable
        );
        VBox.setVgrow(step1ManifestTable, Priority.ALWAYS);
        return box;
    }

    @SuppressWarnings("unchecked")
    private TableView<TableInfoRow> buildManifestPreviewTable() {
        TableView<TableInfoRow> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<TableInfoRow, String> colTable = new TableColumn<>("Table");
        colTable.setCellValueFactory(new PropertyValueFactory<>("tableName"));

        TableColumn<TableInfoRow, String> colCols = new TableColumn<>("Columns");
        colCols.setCellValueFactory(new PropertyValueFactory<>("columnCount"));

        TableColumn<TableInfoRow, String> colRows = new TableColumn<>("Row Count");
        colRows.setCellValueFactory(new PropertyValueFactory<>("rowCount"));

        tv.getColumns().addAll(colTable, colCols, colRows);
        tv.setPrefHeight(200);
        return tv;
    }

    private void browseHdmigFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Migration File");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("HawkDesk Migration Files", "*.hdmig"));
        File chosen = chooser.showOpenDialog(getStage());
        if (chosen == null) return;

        try {
            manifest = HdmigFileUtil.readManifest(chosen);
            hdmigFile = chosen;
            step1FilePath.setText(chosen.getAbsolutePath());
            step1ManifestTable.getItems().clear();
            for (TableSchema ts : manifest.getTables()) {
                step1ManifestTable.getItems().add(new TableInfoRow(
                    ts.getTableName(),
                    String.valueOf(ts.getColumns().size()),
                    String.valueOf(ts.getRowCount())
                ));
            }
        } catch (IOException e) {
            showAlert("File Error", "Could not read file: " + e.getMessage());
        }
    }

    // ---- Step 2: Connect to target DB ----

    private Node buildStep2() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");

        credentialCombo = new ComboBox<>();
        credentialCombo.setMaxWidth(Double.MAX_VALUE);
        credentialCombo.setPromptText("Select a saved connection…");
        credentialCombo.getItems().addAll(credentialRepository.loadAll());

        step2Status = new Label();
        step2Status.setWrapText(true);

        Button testBtn = new Button("Test Connection");
        testBtn.setOnAction(e -> testStep2Connection());

        box.getChildren().addAll(
            label("Connect to Target Database", "step-title"),
            label("Choose the database you want to import data into.", "step-subtitle"),
            new Separator(),
            label("Saved Connection:", null),
            credentialCombo,
            testBtn,
            step2Status
        );
        return box;
    }

    private void testStep2Connection() {
        DatabaseCredential cred = credentialCombo.getValue();
        if (cred == null) { showStep2Status("Please select a connection.", true); return; }
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
        step2Status.setText(msg);
        step2Status.setTextFill(error ? Color.RED : Color.GREEN);
    }

    // ---- Step 3: Select tables to import ----

    private Node buildStep3() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");
        tableSelectionBox = new VBox(12);
        ScrollPane scroll = new ScrollPane(tableSelectionBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        box.getChildren().addAll(
            label("Select Tables to Import", "step-title"),
            label("Choose tables and map them to target tables or create new ones.", "step-subtitle"),
            new Separator(),
            scroll
        );
        return box;
    }

    private void buildTableSelectionRows() {
        tableSelectionBox.getChildren().clear();
        tableCheckBoxes.clear();
        targetTableCombos.clear();
        createNewCheckBoxes.clear();
        createSqlAreas.clear();

        if (manifest == null) return;

        for (TableSchema ts : manifest.getTables()) {
            String tableName = ts.getTableName();

            CheckBox selectCb = new CheckBox(tableName);
            selectCb.setSelected(true);
            tableCheckBoxes.put(tableName, selectCb);

            ComboBox<String> targetCombo = new ComboBox<>();
            targetCombo.setPromptText("Select target table…");
            targetCombo.setMaxWidth(Double.MAX_VALUE);
            targetTableCombos.put(tableName, targetCombo);

            CheckBox createNewCb = new CheckBox("Create new table");
            createNewCheckBoxes.put(tableName, createNewCb);

            TextArea sqlArea = new TextArea();
            sqlArea.setPrefRowCount(4);
            sqlArea.setVisible(false);
            sqlArea.setManaged(false);
            createSqlAreas.put(tableName, sqlArea);

            createNewCb.setOnAction(e -> {
                boolean create = createNewCb.isSelected();
                targetCombo.setDisable(create);
                sqlArea.setVisible(create);
                sqlArea.setManaged(create);
                if (create) {
                    sqlArea.setText(databaseService.generateCreateTableSql(tableName, ts.getColumns()));
                }
            });

            GridPane row = new GridPane();
            row.setHgap(8);
            row.setVgap(4);
            ColumnConstraints cc1 = new ColumnConstraints(180);
            ColumnConstraints cc2 = new ColumnConstraints();
            cc2.setHgrow(Priority.ALWAYS);
            row.getColumnConstraints().addAll(cc1, cc2);

            row.add(selectCb, 0, 0);
            row.add(label("Target table:", null), 0, 1);
            row.add(targetCombo, 1, 1);
            row.add(createNewCb, 1, 2);
            row.add(sqlArea, 1, 3);
            row.setPadding(new Insets(8));
            row.getStyleClass().add("card");

            tableSelectionBox.getChildren().add(row);
        }

        loadTargetTablesAsync();
    }

    private void loadTargetTablesAsync() {
        if (selectedCredential == null) return;
        new Thread(() -> {
            try (Connection conn = databaseService.connect(selectedCredential)) {
                List<String> tables = databaseService.getTables(conn);
                Platform.runLater(() -> targetTableCombos.values().forEach(combo -> {
                    combo.getItems().clear();
                    combo.getItems().addAll(tables);
                }));
            } catch (SQLException e) {
                Platform.runLater(() -> showAlert("Error", "Could not load target tables: " + e.getMessage()));
            }
        }).start();
    }

    // ---- Step 4: Column mapping ----

    private Node buildStep4() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");

        mappingAccordion = new Accordion();
        ScrollPane scroll = new ScrollPane(mappingAccordion);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        step4ValidationLabel = new Label();
        step4ValidationLabel.setWrapText(true);

        Button validateBtn = new Button("Validate Mappings (first 10 rows)");
        validateBtn.setOnAction(e -> validateMappings());

        box.getChildren().addAll(
            label("Column Mapping", "step-title"),
            label("Map source columns to target columns. Skip any that should not be imported.", "step-subtitle"),
            new Separator(),
            scroll,
            validateBtn,
            step4ValidationLabel
        );
        return box;
    }

    private void buildMappingAccordion() {
        mappingAccordion.getPanes().clear();
        tableMappingRows.clear();

        List<TableImportConfig> configs = buildPartialImportConfigs();

        for (TableImportConfig config : configs) {
            TableSchema schema = manifest.getTable(config.getSourceTableName());
            if (schema == null) continue;

            List<String> targetCols = getTargetColumnNames(config);
            List<MappingRow> rows = new ArrayList<>();
            GridPane grid = buildMappingGrid(schema, targetCols, rows);

            TitledPane pane = new TitledPane(config.getSourceTableName(), grid);
            mappingAccordion.getPanes().add(pane);
            tableMappingRows.put(config.getSourceTableName(), rows);
        }

        if (!mappingAccordion.getPanes().isEmpty()) {
            mappingAccordion.setExpandedPane(mappingAccordion.getPanes().get(0));
        }
    }

    private List<String> getTargetColumnNames(TableImportConfig config) {
        if (config.isCreateNewTable()) {
            TableSchema schema = manifest.getTable(config.getSourceTableName());
            return schema != null ? schema.getColumns().stream().map(ColumnDefinition::getName).collect(Collectors.toList()) : List.of();
        }

        try (Connection conn = databaseService.connect(selectedCredential)) {
            return databaseService.getTableSchema(conn, config.getTargetTableName())
                .stream().map(ColumnDefinition::getName).collect(Collectors.toList());
        } catch (SQLException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private GridPane buildMappingGrid(TableSchema schema, List<String> targetCols, List<MappingRow> rows) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(8));

        List<String> typeOverrides = List.of(
            ColumnMapping.AUTO_DETECT, "INT", "VARCHAR(255)", "DECIMAL(10,2)", "DATETIME", "BOOLEAN", ColumnMapping.SKIP
        );

        List<String> targetOptions = new ArrayList<>();
        targetOptions.add(ColumnMapping.SKIP);
        targetOptions.addAll(targetCols);

        grid.add(label("Source Column", "header-label"), 0, 0);
        grid.add(label("Source Type", "header-label"), 1, 0);
        grid.add(label("→", null), 2, 0);
        grid.add(label("Target Column", "header-label"), 3, 0);
        grid.add(label("Type Override", "header-label"), 4, 0);
        grid.add(label("⚠", "header-label"), 5, 0);

        int row = 1;
        for (ColumnDefinition col : schema.getColumns()) {
            Label srcName = new Label(col.getName());
            Label srcType = new Label(col.getSqlType());
            Label arrow = new Label("→");

            ComboBox<String> targetCombo = new ComboBox<>();
            targetCombo.getItems().addAll(targetOptions);
            targetCombo.setValue(targetCols.contains(col.getName()) ? col.getName() : ColumnMapping.SKIP);
            targetCombo.setMaxWidth(160);

            ComboBox<String> typeCombo = new ComboBox<>();
            typeCombo.getItems().addAll(typeOverrides);
            typeCombo.setValue(ColumnMapping.AUTO_DETECT);
            typeCombo.setMaxWidth(140);

            Label warnLabel = new Label();

            targetCombo.setOnAction(e -> updateMismatchWarning(warnLabel, col.getSqlType(),
                targetCombo.getValue(), typeCombo.getValue()));
            typeCombo.setOnAction(e -> updateMismatchWarning(warnLabel, col.getSqlType(),
                targetCombo.getValue(), typeCombo.getValue()));

            grid.add(srcName, 0, row);
            grid.add(srcType, 1, row);
            grid.add(arrow, 2, row);
            grid.add(targetCombo, 3, row);
            grid.add(typeCombo, 4, row);
            grid.add(warnLabel, 5, row);

            rows.add(new MappingRow(col, targetCombo, typeCombo));
            row++;
        }

        ColumnConstraints cc0 = new ColumnConstraints(140);
        ColumnConstraints cc1 = new ColumnConstraints(120);
        ColumnConstraints cc2 = new ColumnConstraints(20);
        ColumnConstraints cc3 = new ColumnConstraints(160);
        ColumnConstraints cc4 = new ColumnConstraints(140);
        ColumnConstraints cc5 = new ColumnConstraints(30);
        grid.getColumnConstraints().addAll(cc0, cc1, cc2, cc3, cc4, cc5);
        return grid;
    }

    private void updateMismatchWarning(Label label, String srcType, String targetCol, String override) {
        if (ColumnMapping.SKIP.equals(targetCol) || ColumnMapping.AUTO_DETECT.equals(override)) {
            label.setText("");
            return;
        }
        String effectiveTarget = ColumnMapping.AUTO_DETECT.equals(override) ? srcType : override;
        boolean mismatch = typeConverter.isTypeMismatch(srcType, effectiveTarget);
        label.setText(mismatch ? "⚠" : "");
        label.setTextFill(Color.ORANGE);
        if (mismatch) Tooltip.install(label, new Tooltip("Potential type incompatibility between " + srcType + " and " + effectiveTarget));
    }

    private void validateMappings() {
        step4ValidationLabel.setText("Validating…");
        step4ValidationLabel.setTextFill(Color.GRAY);

        List<TableImportConfig> configs = buildImportConfigs();
        new Thread(() -> {
            List<String> issues = new ArrayList<>();
            for (TableImportConfig cfg : configs) {
                List<String[]> tableIssues = importService.validateMappings(hdmigFile, cfg, 10);
                tableIssues.forEach(i -> issues.add("Table " + i[0] + " row " + i[1] + " col " + i[2] + ": " + i[3]));
            }
            Platform.runLater(() -> {
                if (issues.isEmpty()) {
                    step4ValidationLabel.setText("Validation passed — no issues found in first 10 rows.");
                    step4ValidationLabel.setTextFill(Color.GREEN);
                } else {
                    step4ValidationLabel.setText("Issues found:\n" + String.join("\n", issues));
                    step4ValidationLabel.setTextFill(Color.DARKORANGE);
                }
            });
        }).start();
    }

    // ---- Step 5: Execute import ----

    private Node buildStep5() {
        VBox box = new VBox(16);
        box.getStyleClass().add("step-content");

        step5Summary = new Label();
        step5Summary.setWrapText(true);

        step5Progress = new VBox(8);

        step5Log = new TextArea();
        step5Log.setEditable(false);
        step5Log.setPrefHeight(180);
        VBox.setVgrow(step5Log, Priority.ALWAYS);

        btnStartImport = new Button("Start Import");
        btnStartImport.setDefaultButton(true);
        btnStartImport.setOnAction(e -> runImport());

        btnExportErrors = new Button("Export Error Log");
        btnExportErrors.setDisable(true);
        btnExportErrors.setOnAction(e -> exportErrorLog());

        box.getChildren().addAll(
            label("Execute Import", "step-title"),
            label("Review and start the import process.", "step-subtitle"),
            new Separator(),
            step5Summary,
            step5Progress,
            new HBox(8, btnStartImport, btnExportErrors),
            label("Log:", null),
            step5Log
        );
        return box;
    }

    private void buildStep5Summary() {
        List<TableImportConfig> configs = buildImportConfigs();
        StringBuilder sb = new StringBuilder();
        for (TableImportConfig cfg : configs) {
            TableSchema ts = manifest.getTable(cfg.getSourceTableName());
            long rows = ts != null ? ts.getRowCount() : 0;
            sb.append("• ").append(cfg.getSourceTableName()).append(" → ").append(cfg.getTargetTableName())
              .append("  (").append(rows).append(" rows)\n");
        }
        step5Summary.setText(sb.toString().trim());
        step5Log.clear();
        step5Progress.getChildren().clear();
        btnExportErrors.setDisable(true);
        lastImportResult = null;
    }

    private void runImport() {
        btnStartImport.setDisable(true);
        step5Log.clear();
        step5Progress.getChildren().clear();

        List<TableImportConfig> configs = buildImportConfigs();
        Map<String, ProgressBar> progressBars = new LinkedHashMap<>();
        for (TableImportConfig cfg : configs) {
            Label lbl = new Label(cfg.getSourceTableName() + " → " + cfg.getTargetTableName());
            ProgressBar pb = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
            pb.setPrefWidth(400);
            progressBars.put(cfg.getSourceTableName(), pb);
            step5Progress.getChildren().add(new VBox(4, lbl, pb));
        }

        Task<ImportResult> task = new Task<>() {
            @Override
            protected ImportResult call() throws Exception {
                try (Connection conn = databaseService.connect(selectedCredential)) {
                    return importService.importData(hdmigFile, conn, configs,
                        msg -> Platform.runLater(() -> step5Log.appendText(msg + "\n")),
                        (table, progress) -> Platform.runLater(() -> {
                            ProgressBar pb = progressBars.get(table);
                            if (pb != null && progress >= 0) pb.setProgress(progress / 100.0);
                        })
                    );
                }
            }
        };

        task.setOnSucceeded(e -> {
            lastImportResult = task.getValue();
            progressBars.values().forEach(pb -> pb.setProgress(lastImportResult.isSuccess() ? 1.0 : pb.getProgress()));
            String status = lastImportResult.isSuccess() ? "Success" : "Partial failure";
            String desc = lastImportResult.getTablesImported() + " tables, " + lastImportResult.getTotalRowsImported() +
                " rows imported" + (lastImportResult.getFailedRowCount() > 0 ? ", " + lastImportResult.getFailedRowCount() + " rows failed" : "");
            activityRepository.record(new ActivityRecord(
                ActivityRecord.ActivityType.IMPORT, LocalDateTime.now(), status, desc));
            step5Log.appendText("\nImport complete. " + desc);
            if (lastImportResult.getFailedRowCount() > 0) {
                btnExportErrors.setDisable(false);
            }
            btnStartImport.setDisable(false);
        });

        task.setOnFailed(e -> {
            step5Log.appendText("Fatal error: " + task.getException().getMessage());
            btnStartImport.setDisable(false);
        });

        new Thread(task).start();
    }

    private void exportErrorLog() {
        if (lastImportResult == null || lastImportResult.getFailedRows().isEmpty()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Error Log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("import_errors.csv");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;

        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.writeNext(new String[]{"table", "row_number", "source_data", "error_message"});
            for (FailedRow fr : lastImportResult.getFailedRows()) {
                writer.writeNext(new String[]{fr.getTableName(), String.valueOf(fr.getRowNumber()),
                    fr.getSourceData(), fr.getErrorMessage()});
            }
        } catch (IOException e) {
            showAlert("Export Error", "Could not save error log: " + e.getMessage());
        }
    }

    // ---- Navigation ----

    @FXML
    private void onBack() { showStep(currentStep - 1); }

    @FXML
    private void onNext() {
        if (!validateStep(currentStep)) return;
        if (currentStep == TOTAL_STEPS - 1) return;
        prepareStep(currentStep + 1);
        showStep(currentStep + 1);
    }

    private boolean validateStep(int step) {
        return switch (step) {
            case 0 -> {
                if (hdmigFile == null || manifest == null) {
                    showAlert("Validation", "Please select a .hdmig file first.");
                    yield false;
                }
                yield true;
            }
            case 1 -> {
                if (selectedCredential == null) {
                    showStep2Status("Please test the connection first.", true);
                    yield false;
                }
                yield true;
            }
            case 2 -> {
                List<String> selected = tableCheckBoxes.entrySet().stream()
                    .filter(e -> e.getValue().isSelected())
                    .map(Map.Entry::getKey).collect(Collectors.toList());
                if (selected.isEmpty()) {
                    showAlert("Validation", "Please select at least one table.");
                    yield false;
                }
                yield true;
            }
            case 3 -> true;
            default -> true;
        };
    }

    private void prepareStep(int step) {
        switch (step) {
            case 2 -> buildTableSelectionRows();
            case 3 -> buildMappingAccordion();
            case 4 -> buildStep5Summary();
        }
    }

    private List<TableImportConfig> buildPartialImportConfigs() {
        return tableCheckBoxes.entrySet().stream()
            .filter(e -> e.getValue().isSelected())
            .map(e -> {
                String src = e.getKey();
                TableImportConfig cfg = new TableImportConfig();
                cfg.setSourceTableName(src);
                boolean createNew = createNewCheckBoxes.getOrDefault(src, new CheckBox()).isSelected();
                cfg.setCreateNewTable(createNew);
                if (createNew) {
                    cfg.setTargetTableName(src);
                    cfg.setCreateTableStatement(createSqlAreas.get(src).getText());
                } else {
                    String target = Optional.ofNullable(targetTableCombos.get(src))
                        .map(ComboBox::getValue).orElse(src);
                    cfg.setTargetTableName(target != null ? target : src);
                }
                return cfg;
            }).collect(Collectors.toList());
    }

    private List<TableImportConfig> buildImportConfigs() {
        List<TableImportConfig> partials = buildPartialImportConfigs();
        for (TableImportConfig cfg : partials) {
            List<MappingRow> mappingRows = tableMappingRows.getOrDefault(cfg.getSourceTableName(), List.of());
            List<ColumnMapping> mappings = mappingRows.stream().map(mr -> new ColumnMapping(
                mr.column.getName(), mr.column.getSqlType(),
                mr.targetCombo.getValue(), mr.typeCombo.getValue()
            )).collect(Collectors.toList());
            cfg.setColumnMappings(mappings.isEmpty() ? defaultMappings(cfg.getSourceTableName()) : mappings);
        }
        return partials;
    }

    private List<ColumnMapping> defaultMappings(String tableName) {
        TableSchema schema = manifest.getTable(tableName);
        if (schema == null) return List.of();
        return schema.getColumns().stream()
            .map(col -> new ColumnMapping(col.getName(), col.getSqlType(), col.getName(), ColumnMapping.AUTO_DETECT))
            .collect(Collectors.toList());
    }

    private void showStep(int step) {
        for (int i = 0; i < TOTAL_STEPS; i++) stepNodes[i].setVisible(i == step);
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

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.showAndWait();
    }

    private Stage getStage() {
        return (Stage) stepContainer.getScene().getWindow();
    }

    // ---- Inner helpers ----

    public static class TableInfoRow {
        private final String tableName;
        private final String columnCount;
        private final String rowCount;

        public TableInfoRow(String tableName, String columnCount, String rowCount) {
            this.tableName = tableName;
            this.columnCount = columnCount;
            this.rowCount = rowCount;
        }

        public String getTableName() { return tableName; }
        public String getColumnCount() { return columnCount; }
        public String getRowCount() { return rowCount; }
    }

    private record MappingRow(ColumnDefinition column, ComboBox<String> targetCombo, ComboBox<String> typeCombo) {}
}
