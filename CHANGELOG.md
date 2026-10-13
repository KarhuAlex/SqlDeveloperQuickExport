# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- SQL Developer extension: **Quick Export to CSV** and **Quick Export to Excel (.xlsx)** on the query
  result grid's right-click menu. Verified on SQL Developer 24.3.1.347.1826.
- File chooser with a timestamped default name that remembers the last folder; overwrite confirmation.
- Export of the complete result by re-running the grid's query on a separate session, streamed to the file.
- Background export with progress window and Cancel; completion message with Open folder; readable errors
  (file locked by Excel, no permission, query failed). A cancelled or failed export leaves no partial file and
  does not touch an existing one.
- Settings file (delimiter, decimal separator, BOM, LOB length) in the SQL Developer user directory.
- Install zip for Help > Check for Updates > Install From Local File, with a first-run hook that activates
  the extension (see docs/grid-discovery.md).
- `core` module: streaming CSV writer (UTF-8 with optional BOM, configurable delimiter and decimal
  separator, RFC 4180 quoting, ISO dates, NULL as empty).
- `core` module: streaming XLSX writer on Apache POI SXSSF (bold header, frozen header row, autofilter,
  typed number/date/boolean cells, column widths, sheet rollover at Excel's row limit, early failure beyond
  Excel's 16,384 columns).
- `core` module: JDBC result set adapter (plain Java types, CLOB truncation with marker, BLOB placeholder).
- `core` module: atomic file writing (temp file, then move) and a fail-fast "file is locked" check.
- CI workflow that builds and tests `core` only.

### Known limitations
- Queries with bind variables are refused with a message.
- The export runs in a new database session, so `ALTER SESSION` settings are not applied.
