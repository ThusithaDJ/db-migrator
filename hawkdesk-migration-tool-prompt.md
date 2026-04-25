# HawkDesk Migration Tool — Claude Code Prompt

## Project Overview

Build a desktop database migration tool called **HawkDesk Migration Tool** using Java with JavaFX. This tool is designed to help existing HawkDeskPOS users migrate their data to updated database schemas. It supports exporting data from a source database to a `.hdmig` file and importing that file into a target database, with full user control over column selection and type mapping.

---

## Tech Stack

| Component | Choice |
|---|---|
| Language | Java 17+ |
| UI Framework | JavaFX (FXML + Controllers) |
| Build Tool | Maven |
| Database Drivers | MySQL Connector/J, Microsoft JDBC Driver for SQL Server |
| File Format | `.hdmig` (custom — ZIP archive containing `manifest.json` + per-table CSVs) |
| JSON Parsing | Jackson (`com.fasterxml.jackson`) |
| Architecture | MVC with JavaFX Controllers, Service layer, Repository layer |

---

## Project Structure

```
com.hawkdesk.migrationtool
├── Main.java
├── controller/
│   ├── DashboardController.java
│   ├── SettingsController.java
│   ├── ExportWizardController.java
│   └── ImportWizardController.java
├── service/
│   ├── DatabaseService.java
│   ├── ExportService.java
│   ├── ImportService.java
│   └── TypeConversionService.java
├── model/
│   ├── DatabaseCredential.java
│   ├── TableSchema.java
│   ├── ColumnDefinition.java
│   ├── ColumnMapping.java
│   └── MigrationManifest.java
├── repository/
│   └── CredentialRepository.java       ← persists credentials to local JSON file
├── util/
│   ├── HdmigFileUtil.java              ← ZIP read/write for .hdmig format
│   └── ConnectionFactory.java
└── resources/
    ├── fxml/
    │   ├── main.fxml
    │   ├── dashboard.fxml
    │   ├── settings.fxml
    │   ├── export_wizard.fxml
    │   └── import_wizard.fxml
    └── css/
        └── theme.css
```

---

## .hdmig File Format Specification

A `.hdmig` file is a ZIP archive with the following structure:

```
archive.hdmig  (ZIP)
├── manifest.json
└── data/
    ├── table1.csv
    ├── table2.csv
    └── ...
```

### manifest.json structure

```json
{
  "exportedAt": "2025-04-23T10:30:00Z",
  "exportedBy": "HawkDesk Migration Tool v1.0",
  "tables": [
    {
      "tableName": "products",
      "rowCount": 532,
      "columns": [
        { "name": "id",         "sqlType": "INT",           "nullable": false, "isPrimaryKey": true  },
        { "name": "name",       "sqlType": "VARCHAR(255)",   "nullable": false, "isPrimaryKey": false },
        { "name": "price",      "sqlType": "DECIMAL(10,2)",  "nullable": true,  "isPrimaryKey": false },
        { "name": "created_at", "sqlType": "DATETIME",       "nullable": true,  "isPrimaryKey": false }
      ]
    }
  ]
}
```

CSV files use UTF-8 encoding, comma delimiter, double-quote escaping, and include a header row with column names.

---

## Views & Feature Requirements

### 1. Main Window

- Navigation bar at top with two tabs: **Dashboard** and **Settings**
- Clean, professional JavaFX layout

---

### 2. Settings View (`settings.fxml`)

- **Saved Connections** list — displays all saved DB credentials (name, type, host)
- **Add / Edit Connection** form with fields:
  - Connection name (label)
  - Database type (ComboBox: MySQL, MS SQL Server)
  - Host, Port, Database name, Username, Password
- **Test Connection** button — attempts a live JDBC connection, shows success/failure inline
- Save and Delete buttons per credential
- Credentials are persisted locally to `~/.hawkdeskmig/credentials.json`
  - Passwords stored with basic Base64 obfuscation — note to user that this is not production-grade encryption

---

### 3. Dashboard View (`dashboard.fxml`)

- Two large action buttons: **Export Data** and **Import Data**
- Recent activity log showing the last 5 export/import operations with timestamp and status

---

### 4. Export Wizard (`export_wizard.fxml`)

Step-based wizard using a StackPane with Back / Next / Export navigation.

#### Step 1 — Choose Format
- ComboBox with export format — currently only `.hdmig` is available
- Brief description of the format shown below the dropdown

#### Step 2 — Connect to Source Database
- ComboBox to select a saved DB credential from Settings
- "Test Connection" button
- Proceed button becomes active only on successful connection

#### Step 3 — Select Tables
- All tables from the connected database shown in a CheckBox ListView
- "Select All" / "Deselect All" convenience buttons
- At least one table must be selected to proceed

#### Step 4 — Select Columns (per table)
- For each selected table, show a tab or accordion section
- Each section lists all columns with: CheckBox, column name, SQL type label
- At least one column per table must be selected

#### Step 5 — Export
- Summary: tables selected, estimated row count
- "Choose output location" file picker (saves as `.hdmig`)
- "Export" button — runs export, shows a ProgressBar per table
- On completion: success message with file path, option to open containing folder

---

### 5. Import Wizard (`import_wizard.fxml`)

Step-based wizard using a StackPane with Back / Next / Import navigation.

#### Step 1 — Select Source File
- File picker filtered to `.hdmig` files
- On file selected: parse `manifest.json`, display a summary table (table name, columns, row count)

#### Step 2 — Connect to Target Database
- ComboBox to select a saved DB credential (MySQL or MSSQL)
- "Test Connection" button

#### Step 3 — Select Tables to Import
- List of tables from the `.hdmig` manifest, each with a CheckBox
- For each selected table, user chooses the target table via:
  - ComboBox showing existing tables in the target DB
  - **OR** a "Create new table" option that auto-generates a `CREATE TABLE` statement from the manifest schema — user can review and edit the statement before executing

#### Step 4 — Column Mapping (per table)
For each source column, show a row with:

| Field | Description |
|---|---|
| Source column name | From manifest — read-only |
| Source SQL type | From manifest — read-only |
| `→` arrow | Visual separator |
| Target column | ComboBox — select target column or "Skip this column" |
| Type override | ComboBox — auto-detect or force: INT, VARCHAR, DECIMAL, DATETIME, BOOLEAN, Skip |

- Warning icon shown when source and target types are potentially incompatible
- **"Validate Mappings"** button — dry-run type check on the first 10 rows of the CSV, reports any issues before committing

#### Step 5 — Execute Import
- Summary of tables, row counts, and any warnings from the validation step
- "Start Import" button
- ProgressBar per table
- Real-time log panel showing current row number and any rows skipped with reason
- On completion: success/failure summary
- Option to export an **error log** `.csv` for any failed rows (columns: `table`, `row_number`, `source_data`, `error_message`)

---

## Service Layer Requirements

### DatabaseService

```java
Connection connect(DatabaseCredential credential);
boolean testConnection(DatabaseCredential credential);   // returns true/false + populates error message
List<String> getTables(Connection conn);
List<ColumnDefinition> getTableSchema(Connection conn, String tableName);
boolean tableExists(Connection conn, String tableName);
void createTable(Connection conn, String tableName, List<ColumnDefinition> columns);
void executeInsert(Connection conn, String tableName, Map<String, Object> rowData);
```

### ExportService

```java
ExportResult export(Connection conn, List<TableExportConfig> configs, File outputFile);
```

- Reads each table with `SELECT` respecting the selected column list
- Writes each table to a CSV stream (OpenCSV)
- Writes `manifest.json` (Jackson)
- ZIPs everything into the `.hdmig` output file

### ImportService

```java
ImportResult importData(File hdmigFile, Connection targetConn, List<TableImportConfig> configs);
```

- For each table config: reads CSV row by row, applies column mappings and type conversions, calls `DatabaseService.executeInsert`
- Catches per-row exceptions, logs failed rows, continues processing remaining rows

### TypeConversionService

```java
Object convert(String rawValue, String targetSqlType, String strategy) throws TypeConversionException;
```

Handles:

| Source | Target | Notes |
|---|---|---|
| String | INT | `Integer.parseInt()` |
| String | DECIMAL | `new BigDecimal()` |
| String | DATETIME | Multiple format attempts (ISO, dd/MM/yyyy, etc.) |
| String | BOOLEAN | Accepts: `true`, `false`, `1`, `0`, `yes`, `no` |
| Any | null | Respects column `nullable` flag from manifest |

Throws `TypeConversionException` with a descriptive message on failure.

---

## Error Handling

- All DB operations wrapped in `try-with-resources`
- Connection failures shown as inline error messages in the wizard (not modal popups)
- Per-row import errors are collected and do **not** stop the import — they are written to the error log CSV
- File I/O errors shown as JavaFX `Alert` dialogs with clear messages

---

## Threading Rules

- All DB calls and file I/O must happen on background threads — use JavaFX `Task<>` and `Service<>`
- **Never block the JavaFX Application Thread**
- Progress updates and UI updates use `Platform.runLater()` or `Task.updateProgress()`

---

## Build & Dependencies (`pom.xml`)

```xml
<!-- JavaFX -->
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-controls</artifactId>
  <version>21</version>
</dependency>
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-fxml</artifactId>
  <version>21</version>
</dependency>

<!-- MySQL -->
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>8.3.0</version>
</dependency>

<!-- MS SQL Server -->
<dependency>
  <groupId>com.microsoft.sqlserver</groupId>
  <artifactId>mssql-jdbc</artifactId>
  <version>12.6.1.jre11</version>
</dependency>

<!-- Jackson -->
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.17.0</version>
</dependency>

<!-- OpenCSV -->
<dependency>
  <groupId>com.opencsv</groupId>
  <artifactId>opencsv</artifactId>
  <version>5.9</version>
</dependency>
```

---

## Coding Standards

- Use FXML for all static layouts; programmatic UI construction only for dynamic elements (column mapping rows in the import wizard)
- No third-party UI libraries beyond standard JavaFX controls
- JDBC connections must always use `try-with-resources`
- Each wizard step is a separate FXML fragment loaded dynamically into the wizard `StackPane`
- All user-facing strings in English

---

## Implementation Order

Build in this sequence to ensure each layer is testable before the next is added:

1. Models and manifest format (`MigrationManifest`, `ColumnDefinition`, `ColumnMapping`)
2. `DatabaseService` + `ConnectionFactory`
3. `CredentialRepository` + Settings view (fully working)
4. Export wizard — all 5 steps
5. Import wizard — all 5 steps
6. `TypeConversionService` with all type handlers
7. Error log CSV export

---

## Deliverables

- Full Maven project with all source files
- Working `pom.xml` with all dependencies declared
- All FXML layouts for all views and wizards
- All service, model, controller, and utility classes fully implemented
- `README.md` with: build instructions, run instructions, known limitations
- A sample `manifest.json` demonstrating the expected format
