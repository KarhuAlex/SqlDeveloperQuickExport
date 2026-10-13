package dev.quickexport.core;

/** Per-row bookkeeping shared by the writers: honours cancellation and reports progress. */
final class RowTicker {

    private static final long REPORT_EVERY = 1000;

    private final ExportControl control;
    private long rows;

    RowTicker(ExportControl control) {
        this.control = control;
    }

    /** Call before writing a row. */
    void beforeRow() throws ExportCancelledException {
        if (control.isCancelled()) {
            throw new ExportCancelledException();
        }
    }

    /** Call after a row has been written. */
    void afterRow() {
        if (++rows % REPORT_EVERY == 0) {
            control.progress(rows);
        }
    }

    /** Reports the final count and returns it. */
    long finish() {
        control.progress(rows);
        return rows;
    }
}
