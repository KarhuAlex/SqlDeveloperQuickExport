package dev.quickexport.sqldev;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import dev.quickexport.core.CsvWriter;
import dev.quickexport.core.ExportControl;
import dev.quickexport.core.ExportModel;
import dev.quickexport.core.ExportResult;
import dev.quickexport.core.ExportSettings;
import dev.quickexport.core.LimitedWriter;

/**
 * Copies the result to the system clipboard as CSV text, with the delimiter and decimal separator
 * from the settings and no byte order mark. The text is held in memory, so the size is limited;
 * the clipboard changes only when the whole result has been read.
 */
final class ClipboardOutput implements ExportOutput {

    private static final int COPY_ATTEMPTS = 10;
    private static final long COPY_RETRY_MILLIS = 100;

    private final ExportSettings settings;

    ClipboardOutput(ExportSettings settings) {
        this.settings = settings;
    }

    @Override
    public String label() {
        return "Clipboard  (CSV)";
    }

    @Override
    public Optional<Path> file() {
        return Optional.empty();
    }

    @Override
    public void check() throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IOException("The clipboard is not available.");
        }
    }

    @Override
    public ExportResult write(ExportModel model, ExportControl control) throws IOException {
        LimitedWriter text = new LimitedWriter(settings.clipboardMaxChars());
        ExportResult result = new CsvWriter(settings).write(model, text, control);
        copy(text.toString());
        return result;
    }

    /** Windows lets only one program have the clipboard open, so a busy clipboard is retried briefly. */
    private static void copy(String text) throws IOException {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        for (int attempt = 1;; attempt++) {
            try {
                clipboard.setContents(new StringSelection(text), null);
                return;
            } catch (IllegalStateException e) {
                if (attempt == COPY_ATTEMPTS) {
                    throw new IOException("The clipboard is in use by another program. Try again in a moment.", e);
                }
                try {
                    Thread.sleep(COPY_RETRY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Copying to the clipboard was interrupted.", interrupted);
                }
            }
        }
    }
}
