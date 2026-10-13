package dev.quickexport.core;

/** Lets the caller observe progress and request cancellation of a running export. */
public interface ExportControl {

    /** A control that reports nothing and never cancels. */
    ExportControl NONE = new ExportControl() { };

    /** Called periodically with the number of data rows written so far. */
    default void progress(long rowsWritten) { }

    /** Polled once per row; when true the writer stops with {@link ExportCancelledException}. */
    default boolean isCancelled() {
        return false;
    }
}
