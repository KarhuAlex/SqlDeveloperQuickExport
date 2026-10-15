package dev.quickexport.sqldev;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import dev.quickexport.core.AtomicFiles;
import dev.quickexport.core.CsvWriter;
import dev.quickexport.core.ExportControl;
import dev.quickexport.core.ExportModel;
import dev.quickexport.core.ExportResult;
import dev.quickexport.core.ExportSettings;
import dev.quickexport.core.XlsxWriter;

/**
 * Writes a CSV or Excel file. The data goes to a temporary file that replaces the target only
 * when the export is complete.
 */
final class FileOutput implements ExportOutput {

    private final ExportFormat format;
    private final Path target;
    private final ExportSettings settings;
    private final String query;

    FileOutput(ExportFormat format, Path target, ExportSettings settings, String query) {
        this.format = format;
        this.target = target;
        this.settings = settings;
        this.query = query;
    }

    @Override
    public String label() {
        return target.getFileName() + "  (" + format.shortName + ")";
    }

    @Override
    public Optional<Path> file() {
        return Optional.of(target);
    }

    @Override
    public void check() throws IOException {
        AtomicFiles.checkWritable(target); // fail fast, e.g. when Excel has the file open
    }

    @Override
    public ExportResult write(ExportModel model, ExportControl control) throws IOException {
        return AtomicFiles.write(target, out -> format == ExportFormat.CSV
                ? new CsvWriter(settings).write(model, out, control)
                : new XlsxWriter(settings.querySheet() ? query : null).write(model, out, control));
    }
}
