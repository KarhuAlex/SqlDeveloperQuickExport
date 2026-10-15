package dev.quickexport.sqldev;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import dev.quickexport.core.ExportCancelledException;
import dev.quickexport.core.ExportControl;
import dev.quickexport.core.ExportResult;
import dev.quickexport.core.ExportSettings;
import dev.quickexport.core.JdbcExportModel;
import dev.quickexport.core.SqlText;
import oracle.dbtools.raptor.utils.Connections;

/**
 * One export, run on its own thread. The grid's query is executed again on a separate session
 * (so the grid and the user's worksheet session are left alone) and the rows are streamed to the
 * output, which is a file or the clipboard.
 */
final class ExportJob implements Runnable, ExportControl {

    /** Callbacks from the export thread, always delivered on the event thread. */
    interface Listener {
        void rows(long rowsWritten);

        /** The number of rows the query returns; may never arrive if the count fails or is disabled. */
        void total(long totalRows);

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
    private final ExportOutput output;
    private final ExportSettings settings;
    private volatile Listener listener;
    private volatile boolean cancelled;
    private volatile Statement running;
    private volatile boolean countStopped;
    private volatile Statement counting;

    ExportJob(GridSource source, ExportOutput output, ExportSettings settings) {
        this.source = source;
        this.output = output;
        this.settings = settings;
    }

    void start(Listener listener) {
        this.listener = listener;
        startThread(this, "Quick Export");
    }

    private static void startThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Called from the event thread when the user presses Cancel. */
    void cancel() {
        cancelled = true;
        cancelStatement(running);
        stopCounting();
    }

    private static void cancelStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.cancel(); // frees a slow query; the writer also polls the flag per row
            } catch (SQLException e) {
                LOG.log(Level.FINE, "Statement cancel failed", e);
            }
        }
    }

    private void stopCounting() {
        countStopped = true;
        cancelStatement(counting);
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
        } finally {
            stopCounting();
        }
    }

    /**
     * Counts the rows the query returns on a second session, so the progress window can show a
     * percentage. Best effort: if it fails or finishes after the export, nothing is reported.
     */
    private void countRows() {
        try (Connection connection = Connections.getInstance().cloneConnection(source.connectionName());
                Statement statement = connection.createStatement()) {
            counting = statement;
            if (countStopped) {
                return;
            }
            try (ResultSet result = statement.executeQuery(SqlText.countQuery(source.sql()))) {
                if (result.next() && !countStopped) {
                    long total = result.getLong(1);
                    SwingUtilities.invokeLater(() -> listener.total(total));
                }
            }
        } catch (Throwable t) {
            if (!countStopped) {
                LOG.log(Level.FINE, "Row count unavailable, progress stays without a percentage", t);
            }
        } finally {
            counting = null;
        }
    }

    private Outcome execute() throws Exception {
        output.check();
        try (Connection connection = Connections.getInstance().cloneConnection(source.connectionName());
                Statement statement = connection.createStatement()) {
            statement.setFetchSize(FETCH_SIZE);
            running = statement;
            if (cancelled) {
                throw new ExportCancelledException();
            }
            if (settings.countRows()) {
                // Started only now, so a login prompt for the connection cannot appear twice.
                startThread(this::countRows, "Quick Export row count");
            }
            try (ResultSet rows = statement.executeQuery(source.sql())) {
                JdbcExportModel model = new JdbcExportModel(rows, settings.lobMaxChars());
                ExportResult result = output.write(model, this);
                return new Outcome(result, result.truncatedCells() + model.getTruncatedLobs());
            }
        } finally {
            running = null;
        }
    }
}
