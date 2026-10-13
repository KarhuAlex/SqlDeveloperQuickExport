# SQL Developer Quick Export

<img width="866" height="419" alt="Preview" src="https://github.com/user-attachments/assets/217d7801-f602-4fd8-97b2-2aec63b1494e" />

CSV and Excel (.xlsx) export from the Oracle SQL Developer query result grid.

Right-click a result grid, choose Quick Export to CSV or Quick Export to Excel (.xlsx), and pick a file name.

This project is not affiliated with or endorsed by Oracle. "Oracle" and "SQL Developer" are trademarks of Oracle and/or its affiliates.

## Why

SQL Developer's built-in export is a wizard that asks for a format, delimiters, encoding and other options
every time. Usually you just want this result as CSV or Excel in a folder.

## What you get

- Two items on the result grid's right-click menu.
- A file chooser that opens in the folder you used last, with a name like `export_20261013_142530.csv`.
  Replacing an existing file asks first.
- All rows of the query, including those the grid has not fetched yet.
- In Excel exports, the query on a second sheet named Query.
- A background export with a progress window that shows rows and percent, and a Cancel button. A cancelled or failed export never
  leaves a half-written file and never damages an existing one.
- A completion message with Open file and Open folder buttons, and plain-language errors such as a file locked by
  Excel, a missing permission or a failed query.

## Install

Tested with SQL Developer 24.3.1 on Windows. Other versions are untested.

1. Download `quick-export-sqldeveloper-<version>-install.zip`, or build it (see below).
2. In SQL Developer, choose Help > Check for Updates > Install From Local File and pick the zip.
3. Let SQL Developer restart. The extension loads itself on that first restart and on every later start.
4. Run a query and right-click the result grid.

Don't copy the jar into `sqldeveloper/extensions` or `dropins` by hand. SQL Developer registers the
extension that way but never starts it.

To uninstall, use the list of installed updates under Check for Updates. This is untested. If it fails,
delete `sqldeveloper/extensions/quick-export-sqldeveloper-*.jar` and the `dev.quickexport.sqldeveloper`
lines in `configuration/cfu_bundles.info` and `configuration/cfu_bundles.extras` in the SQL Developer
install folder.

## Build from source

You need JDK 17 or newer and Maven.

```bash
# Oracle-independent part only: writers and their tests (this is what CI runs)
mvn verify

# Everything, including the SQL Developer extension and the install zip.
# -Dsqldev.home is the folder of your SQL Developer that contains ide/, sqldeveloper/ and jdk/
mvn package -Dsqldev.home="C:/path/to/sqldeveloper-24.3.1-x64/sqldeveloper"
```

The output is `extension/target/quick-export-sqldeveloper-<version>-install.zip`, with the bare jar next to it.
The extension compiles against jars read from your local SQL Developer installation. They are never copied
into the repository or the built jar. Apache POI and its dependencies are bundled and relocated.

Modules:

- `core/`: pure Java. `ExportModel`, `CsvWriter`, `XlsxWriter`, `JdbcExportModel` and their tests. No Oracle imports.
- `extension/`: the SQL Developer integration (menu item, grid access, file chooser, background job).
