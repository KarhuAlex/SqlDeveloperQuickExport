package dev.quickexport.core;

/**
 * Outcome of a finished export.
 *
 * @param rows            data rows written (header not counted)
 * @param truncatedCells  cells whose text was cut to fit the target format (XLSX cell limit)
 * @param sheets          worksheets that hold data (always 1 for CSV); a query worksheet is not counted
 */
public record ExportResult(long rows, long truncatedCells, int sheets) {
}
