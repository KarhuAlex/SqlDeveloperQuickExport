package dev.quickexport.sqldev;

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
 * One export, run on its own thread. The grid's query is executed again on a separate
 * session (so the grid and the user's worksheet session are left alone), and the rows are
 * streamed straight into a temporary file that replaces the target only when complete.
 */
final class ExportJob implements Runnable, ExportControl {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final int FETCH_SIZE = 1000;

    private final GridSource source;
    private final ExportFormat format;
    private final Path target;
    private final ExportSettings settings;
    private final ExportUi.Progress progress;

    private volatile boolean cancelled;
    private volatile Statement running;

    ExportJob(GridSource source, ExportFormat format, Path target, ExportSettings settings, ExportUi.Progress progress) {
        this.source = source;
        this.format = format;
        this.target = target;
        this.settings = settings;
        this.progress = progress;
    }

    void start() {
        Thread t = new Thread(this, "Quick Export");
        t.setDaemon(true);
        t.start();
    }

    /** Called from the event thread when the user presses Cancel. */
    void cancel() {
        cancelled = true;
        Statement st = running;
        if (st != null) {
            try {
                st.cancel(); // frees a long-running first fetch; rows are also polled in the writer
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
        SwingUtilities.invokeLater(() -> progress.rows(rowsWritten));
    }

    @Override
    public void run() {
        try {
            AtomicFiles.checkWritable(target); // fail fast, e.g. when Excel has the file open
            ExportResult result;
            long truncatedLobs;
            Connection conn = Connections.getInstance().cloneConnection(source.connectionName);
            try (Connection c = conn; Statement st = c.createStatement()) {
                st.setFetchSize(FETCH_SIZE);
                running = st;
                if (cancelled) {
                    throw new ExportCancelledException();
                }
                try (ResultSet rs = st.executeQuery(source.sql)) {
                    JdbcExportModel model = new JdbcExportModel(rs, settings.lobMaxChars());
                    ExportResult[] holder = new ExportResult[1];
                    AtomicFiles.write(target, out -> holder[0] = writeTo(model, out));
                    result = holder[0];
                    truncatedLobs = model.getTruncatedLobs();
                }
            } finally {
                running = null;
            }
            long lobs = truncatedLobs;
            SwingUtilities.invokeLater(() -> progress.finished(result, lobs));
        } catch (ExportCancelledException e) {
            SwingUtilities.invokeLater(progress::cancelled);
        } catch (Throwable t) {
            if (cancelled) { // a cancel() on the statement surfaces as an SQLException
                SwingUtilities.invokeLater(progress::cancelled);
                return;
            }
            LOG.log(Level.WARNING, "Quick Export failed", t);
            SwingUtilities.invokeLater(() -> progress.failed(t));
        }
    }

    private ExportResult writeTo(JdbcExportModel model, java.io.OutputStream out) throws java.io.IOException {
        return format == ExportFormat.CSV
                ? new CsvWriter(settings).write(model, out, this)
                : new XlsxWriter().write(model, out, this);
    }
}
