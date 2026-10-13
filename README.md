# SQL Developer Quick Export

One-click **CSV** and **Excel (.xlsx)** export from the Oracle SQL Developer query result grid.

Right-click a result grid, choose **Quick Export to CSV** or **Quick Export to Excel (.xlsx)**, pick a
file name, done. No wizard, no format dialogs, no settings to click through.

> **Not affiliated with or endorsed by Oracle.** "Oracle" and "SQL Developer" are trademarks of
> Oracle and/or its affiliates. This is an independent open-source extension. It contains no
> Oracle code; see [Legal](#legal).

<!-- TODO: replace with a screen recording of the right-click -> save -> done flow -->
![Demo](docs/images/demo.gif)

## Why

SQL Developer's built-in export is a multi-step wizard that asks for a format, delimiters, encodings
and more, every time. Most of the time you want "this result, as CSV or Excel, over there".

## What you get

- Two items on the result grid's right-click menu.
- A file chooser that opens in the folder you used last, with a name like `export_20261013_142530.csv`.
  Replacing an existing file asks first.
- **All rows** of the query, not just the page the grid has fetched (see
  [How the full result is fetched](#how-the-full-result-is-fetched)).
- Runs on a background thread with a progress window and a **Cancel** button. A cancelled or failed
  export never leaves a half-written file and never damages an existing one.
- A completion message with **Open folder**, and readable errors (file locked by Excel, no permission,
  query failed, ...).

### CSV

UTF-8 **with BOM** (so Excel shows Nordic characters correctly), `;` delimiter by default, CRLF line ends,
fields quoted only when needed, NULL as an empty field, ISO dates (`2024-01-31`,
`2024-01-31 13:45:10`), numbers without exponent notation.

### Excel

Streaming writer (Apache POI SXSSF), so large exports do not fill the heap. Bold header row, frozen
header, autofilter, sized columns, numbers as numbers, dates as dates. When a sheet reaches Excel's
1,048,576-row limit the export continues on a new sheet instead of dropping rows.

## Install

Requires SQL Developer 24.3.1 on Windows (see [Compatibility](#compatibility)).

1. Download `quick-export-sqldeveloper-<version>-install.zip` (or build it, see below).
2. In SQL Developer: **Help > Check for Updates > Install From Local File** and pick the zip.
3. Let SQL Developer restart. The extension loads itself on that first restart and on every start after.
4. Run a query, right-click the result grid.

Do **not** just copy the jar into `sqldeveloper/extensions` or `dropins`. SQL Developer then registers the
extension but never starts it. See [docs/grid-discovery.md](docs/grid-discovery.md#why-installing-via-check-for-updates-matters).

**Uninstall:** use the installed-updates list in Check for Updates. (Not yet tested; if it fails, delete
`sqldeveloper/extensions/quick-export-sqldeveloper-*.jar` and the `dev.quickexport.sqldeveloper` lines in
`configuration/cfu_bundles.info` and `cfu_bundles.extras` in the SQL Developer install folder.)

## Compatibility

| SQL Developer | OS | JDK (bundled) | Status |
|---|---|---|---|
| 24.3.1.347.1826 | Windows 11 | Oracle JDK 17.0.13 | Works (manual testing against an Oracle database) |
| Other versions | | | Untested. The extension attaches to internal grid classes; on a mismatch it disables itself and logs a message instead of breaking the IDE. |

Only Oracle database connections have been tried.

## Configuration

Settings live in `quickexport.properties` in a `quickexport` folder inside SQL Developer's user directory,
for example `%APPDATA%\SQL Developer\quickexport\quickexport.properties`. The file is **not created for you**;
create it to override defaults. It is read on every export, so no restart is needed.

```properties
# delimiter: ";" (default), "," or a name: semicolon, comma, tab, pipe
csv.delimiter = ;
# decimal separator in CSV numbers: "." (default) or ","
csv.decimalSeparator = .
# write a UTF-8 byte order mark (needed for Excel to detect UTF-8)
csv.bom = true
# CLOB/LONG values are cut after this many characters
lob.maxChars = 32000
```

If you open the CSV in a Finnish/Nordic-locale Excel, which expects decimal commas, set
`csv.decimalSeparator = ,` (with `;` as the delimiter).

The extension also writes a small `state.properties` next to it, remembering the last export folder.

## How the full result is fetched

SQL Developer fetches rows lazily (50 at a time by default), so the grid usually holds only part of the result.

Quick Export **runs the grid's SQL again on a separate database session** and streams it to the file.

| | Re-run on a separate session (used) | Ask the grid to fetch everything |
|---|---|---|
| Memory | Constant, rows are streamed | The whole result is held in the grid's memory |
| Row types | Plain JDBC types | Oracle driver objects that need converting |
| Grid | Untouched | Its state changes |
| Same data as the grid | Not guaranteed: it is a new query | Same cursor |
| Session state | A new session: `ALTER SESSION` settings in your worksheet are not applied | Same session |

The export is never silently partial. If the query cannot be run again, you get an error message
instead of a short file.

## Limitations

- **Bind variables:** queries that use bind variables are not supported yet and are refused with a message.
  Run the query with the values written into the SQL.
- **Session state:** the export runs in a new session (see above), so settings applied with `ALTER SESSION`,
  and session-specific objects such as global temporary table contents, are not seen.
- **Data may differ** from what the grid shows if tables changed after the query ran.
- **Excel:** at most 16,384 columns (the export fails early with a message; use CSV). NUMBER values with more
  than 15 significant digits and dates before 1900 are written as text so they are not altered. Text over
  32,767 characters is cut and counted in the completion message.
- **LOBs:** a CLOB/LONG is cut after `lob.maxChars` characters, marked `...[truncated, N characters in total]`,
  and counted in the completion message. BLOBs are exported as `[BLOB n bytes]`, RAW as `[BINARY n bytes]`.
- **Dates:** an Oracle DATE always carries a time, so it is exported as `2024-01-31 00:00:00` when the time is midnight.
- **CSV and formulas:** cells starting with `=`, `+`, `-` or `@` are not escaped, so Excel may treat them as
  formulas when you open the file. Do not open exports of untrusted data without checking.
- **Progress** shows rows written, not a percentage; the total is not known in advance.
- Result grids that are not backed by a query (for example script output) cannot be exported.

## Build from source

You need a JDK 17+ and Maven.

```bash
# Oracle-independent part only: writers and their tests (this is what CI runs)
mvn verify

# Everything, including the SQL Developer extension and the install zip.
# -Dsqldev.home is the folder of your SQL Developer that contains ide/, sqldeveloper/ and jdk/
mvn package -Dsqldev.home="C:/path/to/sqldeveloper-24.3.1-x64/sqldeveloper"
```

Output: `extension/target/quick-export-sqldeveloper-<version>-install.zip` (and the bare jar next to it).
The extension is compiled against jars read from your local installation. They are **never** copied
into the repository or the built jar. Apache POI and its dependencies are bundled and relocated.

Modules:

- `core/`: pure Java. `ExportModel`, `CsvWriter`, `XlsxWriter`, `JdbcExportModel`, tests. No Oracle imports.
- `extension/`: the SQL Developer integration (menu item, grid access, file chooser, background job).
- `docs/`: notes on how the grid and extension loading work in each SQL Developer version.

## Legal

- MIT licensed, see [LICENSE](LICENSE).
- Not affiliated with or endorsed by Oracle.
- This repository contains no Oracle jars, decompiled sources or copied Oracle code. Integration points were found by
  reading the public class and method signatures (`javap`) and the extension manifests of a licensed local
  installation, and are described in [docs/grid-discovery.md](docs/grid-discovery.md). Check the license of your
  SQL Developer installation before using this with it.
