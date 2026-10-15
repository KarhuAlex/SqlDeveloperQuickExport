package dev.quickexport.sqldev;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import dev.quickexport.core.ExportControl;
import dev.quickexport.core.ExportModel;
import dev.quickexport.core.ExportResult;

/** Where an export goes: a file or the clipboard. */
interface ExportOutput {

    /** Caption for the progress window. */
    String label();

    /** The file being written, or empty if the output is not a file. */
    Optional<Path> file();

    /** Throws if the output cannot be written, before any work is done. */
    void check() throws IOException;

    /** Writes the whole model. Called once, on the export thread. */
    ExportResult write(ExportModel model, ExportControl control) throws IOException;
}
