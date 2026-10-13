package dev.quickexport.sqldev;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import oracle.dbtools.raptor.controls.grid.AbstractRaptorGridModel;
import oracle.dbtools.raptor.controls.grid.IResultSetTableModel;
import oracle.dbtools.raptor.controls.grid.RaptorGridTable;
import oracle.dbtools.raptor.controls.grid.contextmenu.GridContextMenuItem;
import oracle.ide.Context;
import oracle.ide.Ide;
import oracle.ide.controller.ContextMenu;
import oracle.ide.controller.IdeAction;

/**
 * SPIKE: a single "Quick Export (spike)" item on the grid's right-click menu. Clicking it
 * writes a diagnostic report about the grid model to a text file. This is used to verify
 * the integration points and the value types the grid hands out, before the real exporter
 * is built on top of them.
 */
final class QuickExportMenuItem extends GridContextMenuItem {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final QuickExportMenuItem INSTANCE = new QuickExportMenuItem();

    private final int cmdSpike = Ide.findOrCreateCmdID("dev.quickexport.spike");
    private IdeAction spikeAction;

    private QuickExportMenuItem() {
    }

    static QuickExportMenuItem getInstance() {
        return INSTANCE;
    }

    @Override
    protected boolean canShow(ContextMenu menu) {
        setTableFromContext(menu.getContext());
        return _table != null;
    }

    @Override
    protected void createAndShowMenu(ContextMenu menu) {
        if (spikeAction == null) {
            spikeAction = createAction(cmdSpike, "Quick Export (spike)", null);
        }
        menu.add(menu.createMenuItem(spikeAction));
    }

    @Override
    public boolean handleEvent(IdeAction action, Context context) {
        if (action.getCommandId() != cmdSpike) {
            return false;
        }
        setTableFromContext(context);
        try {
            Path report = Files.createTempFile("quickexport-spike-", ".txt");
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(report, StandardCharsets.UTF_8))) {
                describe(_table, w);
            }
            JOptionPane.showMessageDialog(null, "Quick Export spike works.\nReport written to:\n" + report);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Quick Export spike failed", t);
            JOptionPane.showMessageDialog(null, "Spike failed: " + t);
        }
        return true;
    }

    @Override
    public boolean update(IdeAction action, Context context) {
        if (action.getCommandId() != cmdSpike) {
            return false;
        }
        action.setEnabled(true);
        return true;
    }

    private static void describe(RaptorGridTable table, PrintWriter w) throws IOException {
        w.println("table class      : " + table.getClass().getName());
        AbstractRaptorGridModel model = table.getGridModel();
        w.println("model class      : " + model.getClass().getName());
        w.println("getRowCount      : " + model.getRowCount());
        w.println("loadedRowCount   : " + model.getLoadedRowCount());
        w.println("allRowsFetched   : " + model.allRowsFetched());
        w.println("fetchSize        : " + model.getFetchSize());
        w.println("columnCount      : " + model.getColumnCount());
        w.println("dupColumnNames   : " + model.hasDuplicateColumnNames());
        if (model instanceof IResultSetTableModel rs) {
            w.println("connectionName   : " + rs.getConnectionName());
            w.println("sql              : " + rs.getSql());
            w.println("modifiedSql      : " + rs.getModifiedSql());
            w.println("bindList         : " + rs.getBindList());
            w.println("bindMap          : " + rs.getBindMap());
            w.println("columnSqlTypes   : " + java.util.Arrays.toString(rs.getColumnSqlTypes()));
        }
        int cols = model.getColumnCount();
        for (int c = 0; c < cols; c++) {
            w.println("col " + c + " name=" + model.getColumnName(c) + " class=" + model.getColumnClass(c).getName());
        }
        int rows = Math.min(model.getLoadedRowCount(), 3);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Object v = model.getValueAt(r, c);
                String text = String.valueOf(v);
                w.println("row " + r + " col " + c + " : "
                        + (v == null ? "null" : v.getClass().getName())
                        + " = " + (text.length() > 80 ? text.substring(0, 80) + "..." : text));
            }
        }
    }
}
