# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- `core` module: streaming CSV writer (UTF-8 with optional BOM, configurable delimiter and
  decimal separator, RFC 4180 quoting, ISO dates, NULL as empty).
- `core` module: streaming XLSX writer on Apache POI SXSSF (bold header, frozen header row,
  autofilter, typed number/date/boolean cells, column widths, sheet rollover at Excel's row limit).
- Atomic file writing (temp file, then move) and a fail-fast "file is locked" check.
- CI workflow that builds and tests `core` only.
