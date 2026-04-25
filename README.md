# HawkDesk Migration Tool

A desktop database migration tool for HawkDeskPOS users. Exports data from a source database into a `.hdmig` file and imports it into a target database, with full column-selection and type-mapping control.

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 17 or later |
| Maven | 3.8 or later |
| JavaFX | Bundled via Maven |

---

## Build

```bash
mvn clean package
```

This produces two JARs in `target/`:

| File | Description |
|---|---|
| `hawkdesk-migration-tool-1.0.0.jar` | Thin JAR (requires classpath) |
| `hawkdesk-migration-tool-jar-with-dependencies.jar` | Fat JAR — recommended for distribution |

---

## Run

### With Maven (development)
```bash
mvn javafx:run
```

### With the fat JAR
```bash
java -jar target/hawkdesk-migration-tool-jar-with-dependencies.jar
```

> **Note:** On Java 17+ you may need to add JavaFX module flags if running the thin JAR. The fat JAR handles this automatically.

---

## Usage

### Settings — Add a Connection

1. Open the **Settings** tab.
2. Click **+ New Connection**.
3. Fill in connection details (type, host, port, database, credentials).
4. Click **Test Connection** to verify, then **Save**.

> Passwords are stored as Base64-encoded strings in `~/.hawkdeskmig/credentials.json`. This is obfuscation only — not encryption. Do not store production credentials on shared machines.

---

### Export Data

1. Click **Export Data** on the Dashboard.
2. **Step 1** — Select `.hdmig` as the export format.
3. **Step 2** — Pick a saved connection and test it.
4. **Step 3** — Check which tables to export.
5. **Step 4** — Choose which columns per table to include.
6. **Step 5** — Pick an output location and click **Export**.

The output is a `.hdmig` ZIP archive containing:
- `manifest.json` — table schemas and row counts
- `data/<table>.csv` — one UTF-8 CSV per table

---

### Import Data

1. Click **Import Data** on the Dashboard.
2. **Step 1** — Browse to a `.hdmig` file; the manifest summary is displayed.
3. **Step 2** — Pick a target database connection and test it.
4. **Step 3** — Select which tables to import and map each to an existing target table or create a new one.
5. **Step 4** — Map source columns to target columns and set type overrides. Use **Validate Mappings** to dry-run the first 10 rows.
6. **Step 5** — Click **Start Import**. Progress and a real-time log are shown. After completion, export an error log CSV if any rows failed.

---

## .hdmig File Format

A `.hdmig` file is a standard ZIP archive:

```
archive.hdmig
├── manifest.json       ← metadata: schema, row counts, export timestamp
└── data/
    ├── products.csv
    ├── customers.csv
    └── ...
```

See `samples/manifest.json` for a complete example.

CSV files use:
- Encoding: UTF-8
- Delimiter: comma
- Quoting: double-quote
- Header row: first row contains column names

---

## Configuration & Data Storage

All persistent data is stored under `~/.hawkdeskmig/`:

| File | Contents |
|---|---|
| `credentials.json` | Saved DB connections (passwords Base64-encoded) |
| `activity.json` | Last 5 export/import activity records |

---

## Known Limitations

- **Password security:** Base64 obfuscation only. For production environments, use a secrets manager or OS keychain instead.
- **Large tables:** No batch-insert optimisation. Very large tables (millions of rows) may be slow on import; future versions could add `LOAD DATA INFILE` or bulk-insert support.
- **MS SQL identifiers:** The identifier quoting uses backtick syntax (MySQL). On MS SQL Server, bracket quoting (`[name]`) is standard; a dialect-aware quoting strategy is planned.
- **Schema evolution:** If source and target schemas diverge significantly, manual SQL may be needed before import.
- **No progress percentage on export:** Row-level progress bars show indeterminate progress during read; totals are shown after completion.

---

## Project Structure

```
src/main/java/com/hawkdesk/migrationtool/
├── Main.java
├── controller/          ← JavaFX controllers
├── service/             ← Business logic (Export, Import, DB, TypeConversion)
├── model/               ← POJOs and enums
├── repository/          ← File-backed persistence (credentials, activity)
└── util/                ← ConnectionFactory, HdmigFileUtil

src/main/resources/
├── fxml/                ← FXML layouts for all views
└── css/theme.css        ← Application stylesheet
```
