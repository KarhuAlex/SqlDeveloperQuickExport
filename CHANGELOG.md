# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] - 2026-10-13

### Added
- SQL Developer extension with Quick Export to CSV and Quick Export to Excel (.xlsx) on the query result
  grid's right-click menu. Tested on SQL Developer 24.3.1.347.1826.
- A file chooser with a timestamped default name. It remembers the last folder and asks before replacing a file.
- Export of the complete result: the grid's query runs again on a separate session and streams to the file.
- A background export with a progress window and Cancel, a completion message with Open file and Open folder, and readable
  errors for a file locked by Excel, a missing permission and a failed query. A cancelled or failed export
  leaves no partial file and does not touch an existing one.
- A percentage in the progress window, for example `3 000 / 10 000 rows (30 %)`. The total comes from a row count that
  runs on a second session alongside the export and appears when it is ready. `progress.countRows = false` turns it off.
- The query on a second worksheet named Query in Excel exports, one line per row. The setting `xlsx.querySheet = false` turns it off.
- A settings file for the delimiter, decimal separator, BOM and LOB length, in the SQL Developer user directory.
- An install zip for Help > Check for Updates > Install From Local File. It includes a first-run hook that
  activates the extension.
- `core`: a streaming CSV writer with UTF-8 and optional BOM, configurable delimiter and decimal separator,
  RFC 4180 quoting, ISO dates and NULL as an empty field.
- `core`: a streaming XLSX writer on Apache POI SXSSF with a bold, frozen header row, autofilter, typed
  number, date and boolean cells, sized columns and a new sheet at Excel's row limit. It fails early beyond
  Excel's 16,384 columns.
- `core`: a JDBC result set adapter that reads plain Java types, truncates CLOBs with a marker and exports
  BLOBs as a size placeholder.
- `core`: atomic file writing (temp file, then move) and a fail-fast check for a locked file.
- A CI workflow that builds and tests `core` only.

### Known limitations
- Queries with bind variables are refused with a message.
- The export runs in a new database session, so `ALTER SESSION` settings are not applied.
