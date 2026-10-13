package dev.quickexport.sqldev;

import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.dbtools.raptor.controls.grid.contextmenu.GridContextMenuItem;
import oracle.ide.Context;
import oracle.ide.Ide;
import oracle.ide.controller.ContextMenu;
import oracle.ide.controller.IdeAction;

/** Adds "Quick Export to CSV" and "Quick Export to Excel (.xlsx)" to the result grid's popup menu. */
final class QuickExportMenuItem extends GridContextMenuItem {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final QuickExportMenuItem INSTANCE = new QuickExportMenuItem();

    private final int cmdCsv = Ide.findOrCreateCmdID("dev.quickexport.csv");
    private final int cmdXlsx = Ide.findOrCreateCmdID("dev.quickexport.xlsx");
    private IdeAction csvAction;
    private IdeAction xlsxAction;

    private QuickExportMenuItem() {
    }

    static QuickExportMenuItem getInstance() {
        return INSTANCE;
    }

    @Override
    protected boolean canShow(ContextMenu menu) {
        setTableFromContext(menu.getContext());
        return GridSource.isExportable(_table);
    }

    @Override
    protected void createAndShowMenu(ContextMenu menu) {
        if (csvAction == null) {
            csvAction = createAction(cmdCsv, ExportFormat.CSV.menuLabel, null);
            xlsxAction = createAction(cmdXlsx, ExportFormat.XLSX.menuLabel, null);
        }
        menu.addSeparator();
        menu.add(menu.createMenuItem(csvAction));
        menu.add(menu.createMenuItem(xlsxAction));
    }

    @Override
    public boolean handleEvent(IdeAction action, Context context) {
        ExportFormat format = formatOf(action);
        if (format == null) {
            return false;
        }
        setTableFromContext(context);
        try {
            ExportUi.begin(GridSource.from(_table), format);
        } catch (GridSource.UnsupportedGridException e) {
            ExportUi.showError(null, e.getMessage());
        } catch (RuntimeException | LinkageError e) {
            LOG.log(Level.SEVERE, "Quick Export could not start", e);
            ExportUi.showError(null, "Quick Export could not start:\n" + e);
        }
        return true;
    }

    @Override
    public boolean update(IdeAction action, Context context) {
        if (formatOf(action) == null) {
            return false;
        }
        action.setEnabled(true);
        return true;
    }

    private ExportFormat formatOf(IdeAction action) {
        int id = action.getCommandId();
        if (id == cmdCsv) {
            return ExportFormat.CSV;
        }
        return id == cmdXlsx ? ExportFormat.XLSX : null;
    }
}
