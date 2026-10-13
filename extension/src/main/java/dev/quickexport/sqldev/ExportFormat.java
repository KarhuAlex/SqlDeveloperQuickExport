package dev.quickexport.sqldev;

/** The two output formats offered on the grid's context menu. */
enum ExportFormat {

    CSV(".csv", "CSV files (*.csv)", "Quick Export to CSV"),
    XLSX(".xlsx", "Excel workbook (*.xlsx)", "Quick Export to Excel (.xlsx)");

    final String extension;
    final String filterDescription;
    final String menuLabel;

    ExportFormat(String extension, String filterDescription, String menuLabel) {
        this.extension = extension;
        this.filterDescription = filterDescription;
        this.menuLabel = menuLabel;
    }
}
