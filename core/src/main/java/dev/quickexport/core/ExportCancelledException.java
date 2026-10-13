package dev.quickexport.core;

import java.io.IOException;

/** Thrown by a writer when {@link ExportControl#isCancelled()} turns true. */
public class ExportCancelledException extends IOException {

    private static final long serialVersionUID = 1L;

    public ExportCancelledException() {
        super("Export cancelled");
    }
}
