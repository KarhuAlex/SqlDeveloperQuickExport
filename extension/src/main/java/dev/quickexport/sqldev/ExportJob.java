package dev.quickexport.sqldev;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import dev.quickexport.core.AtomicFiles;
import dev.quickexport.core.CsvWriter;
import dev.quickexport.core.ExportCancelledException;
import dev.quickexport.core.ExportControl;
import dev.quickexport.core.ExportResult;
import dev.quickexport.core.ExportSettings;
import dev.quickexport.core.JdbcExportModel;
import dev.quickexport.core.XlsxWriter;
import oracle.dbtools.raptor.utils.Connections;

/**
 * One export, run on its own thread. The grid's query is executed again on a separate session
 * (so the grid and the user's worksheet session are left alone) and the rows are streamed into a
 * temporary file that replaces the target only when the export is complete.
 */
final class ExportJob implements Runnable, ExportControl {

    /** Callbacks from the export thread, always delivered on the event thread. */
    interface Listener {
        void rows(long rowsWritten);

        /** @param shortenedValues long text values that were cut to fit the file or the settings */
        void finished(ExportResult result, long shortenedValues);

        void cancelled();

        void failed(Throwable error);
    }

    private record Outcome(ExportResult result, long shortenedValues) {
    }

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final int FETCH_SIZE = 1000;

    private final GridSource source;
    private final ExportFormat format;
    private final Path target;
    private final ExportSettings settings;
    private volatile Listener listener;
    private volatile boolean cancelled;
    private volatile Statement running;

    ExportJob(GridSource source, ExportFormat format, Path target, ExportSettings settings) {
        this.source = source;
        this.format = format;
        this.target = target;
        this.settings = settings;
    }

    void start(Listener listener) {
        this.listener = listener;
        Thread thread = new Thread(this, "Quick Export");
        thread.setDaemon(true);
        thread.start();
    }

    /** Called from the event thread when the user presses Cancel. */
    void cancel() {
        cancelled = true;
        Statement statement = running;
        if (statement != null) {
            try {
                statement.cancel(); // frees a slow query; the writer also polls the flag per row
            } catch (SQLException e) {
                LOG.log(Level.FINE, "Statement cancel failed", e);
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void progress(long rowsWritten) {
        SwingUtilities.invokeLater(() -> listener.rows(rowsWritten));
    }

    @Override
    public void run() {
        try {
            Outcome outcome = execute();
            SwingUtilities.invokeLater(() -> listener.finished(outcome.result(), outcome.shortenedValues()));
        } catch (ExportCancelledException e) {
            SwingUtilities.invokeLater(listener::cancelled);
        } catch (Throwable t) {
            if (cancelled) { // cancelling the statement surfaces as an SQLException
                SwingUtilities.invokeLater(listener::cancelled);
                return;
            }
            LOG.log(Level.WARNING, "Quick Export failed", t);
            SwingUtilities.invokeLater(() -> listener.failed(t));
        }
    }

    private Outcome execute() throws Exception {
        AtomicFiles.checkWritable(target); // fail fast, e.g. when Excel has the file open
        try (Connection connection = Connections.getInstance().cloneConnection(source.connectionName());
                Statement statement = connection.createStatement()) {
            statement.setFetchSize(FETCH_SIZE);
            running = statement;
            if (cancelled) {
                throw new ExportCancelledException();
            }
            try (ResultSet rows = statement.executeQuery(source.sql())) {
                JdbcExportModel model = new JdbcExportModel(rows, settings.lobMaxChars());
                ExportResult result = AtomicFiles.write(target, out -> writeTo(model, out));
                return new Outcome(result, result.truncatedCells() + model.getTruncatedLobs());
            }
        } finally {
            running = null;
        }
    }

    private ExportResult writeTo(JdbcExportModel model, OutputStream out) throws IOException {
        return format == ExportFormat.CSV
                ? new CsvWriter(settings).write(model, out, this)
                : new XlsxWriter(settings.querySheet() ? source.sql() : null).write(model, out, this);
    }
}
