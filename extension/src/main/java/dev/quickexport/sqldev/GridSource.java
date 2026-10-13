package dev.quickexport.sqldev;

import dev.quickexport.core.SqlText;
import oracle.dbtools.raptor.controls.grid.AbstractRaptorGridModel;
import oracle.dbtools.raptor.controls.grid.IResultSetTableModel;
import oracle.dbtools.raptor.controls.grid.RaptorGridTable;

/**
 * What the export needs to know about a result grid, copied out on the event thread: the
 * query that produced it and the connection it ran on. The export re-runs that query on a
 * separate session, so it does not depend on how many rows the grid has fetched so far.
 */
final class GridSource {

    final String sql;
    final String connectionName;
    final int loadedRows;
    final boolean allRowsFetched;

    private GridSource(String sql, String connectionName, int loadedRows, boolean allRowsFetched) {
        this.sql = sql;
        this.connectionName = connectionName;
        this.loadedRows = loadedRows;
        this.allRowsFetched = allRowsFetched;
    }

    /** True if the grid is a query result this extension knows how to export. */
    static boolean isExportable(RaptorGridTable table) {
        return table != null && table.getGridModel() instanceof IResultSetTableModel;
    }

    /**
     * @throws UnsupportedGridException with a user-readable reason if the grid cannot be exported
     */
    static GridSource from(RaptorGridTable table) throws UnsupportedGridException {
        AbstractRaptorGridModel model = table.getGridModel();
        if (!(model instanceof IResultSetTableModel)) {
            throw new UnsupportedGridException("This grid is not the result of a query.");
        }
        IResultSetTableModel rs = (IResultSetTableModel) model;

        String sql = rs.getModifiedSql() != null ? rs.getModifiedSql() : rs.getSql();
        if (sql == null || sql.isBlank()) {
            throw new UnsupportedGridException("This grid has no query that can be run again.");
        }
        if ((rs.getBindList() != null && !rs.getBindList().isEmpty())
                || (rs.getBindMap() != null && !rs.getBindMap().isEmpty())) {
            throw new UnsupportedGridException(
                    "This query uses bind variables, which Quick Export cannot re-run yet.\n"
                    + "Run the query again with the values written into the SQL, then export.");
        }
        String connection = rs.getConnectionName();
        if (connection == null || connection.isBlank()) {
            throw new UnsupportedGridException("This grid is not attached to a database connection.");
        }
        return new GridSource(SqlText.stripTerminator(sql), connection, model.getLoadedRowCount(), model.allRowsFetched());
    }

    static final class UnsupportedGridException extends Exception {
        private static final long serialVersionUID = 1L;

        UnsupportedGridException(String message) {
            super(message);
        }
    }
}
