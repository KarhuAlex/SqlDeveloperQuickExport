package dev.quickexport.sqldev;

import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.dbtools.raptor.controls.grid.contextmenu.GridContextMenuItem;
import oracle.ide.Context;
import oracle.ide.Ide;
import oracle.ide.controller.ContextMenu;
import oracle.ide.controller.IdeAction;

/**
 * Adds "Quick Export to CSV", "Quick Export to Excel (.xlsx)" and "Quick Export to Clipboard (CSV)"
 * to the result grid's popup menu.
 */
final class QuickExportMenuItem extends GridContextMenuItem {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final QuickExportMenuItem INSTANCE = new QuickExportMenuItem();
    private static final String CLIPBOARD_LABEL = "Quick Export to Clipboard (CSV)";

    private final int cmdCsv = Ide.findOrCreateCmdID("dev.quickexport.csv");
    private final int cmdXlsx = Ide.findOrCreateCmdID("dev.quickexport.xlsx");
    private final int cmdClipboard = Ide.findOrCreateCmdID("dev.quickexport.clipboard");
    private IdeAction csvAction;
    private IdeAction xlsxAction;
    private IdeAction clipboardAction;

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
            clipboardAction = createAction(cmdClipboard, CLIPBOARD_LABEL, null);
        }
        menu.addSeparator();
        menu.add(menu.createMenuItem(csvAction));
        menu.add(menu.createMenuItem(xlsxAction));
        menu.add(menu.createMenuItem(clipboardAction));
    }

    @Override
    public boolean handleEvent(IdeAction action, Context context) {
        int id = action.getCommandId();
        if (!isOurs(id)) {
            return false;
        }
        setTableFromContext(context);
        try {
            GridSource source = GridSource.from(_table);
            if (id == cmdClipboard) {
                ExportUi.beginClipboard(source);
            } else {
                ExportUi.beginFile(source, id == cmdCsv ? ExportFormat.CSV : ExportFormat.XLSX);
            }
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
        if (!isOurs(action.getCommandId())) {
            return false;
        }
        action.setEnabled(true);
        return true;
    }

    private boolean isOurs(int commandId) {
        return commandId == cmdCsv || commandId == cmdXlsx || commandId == cmdClipboard;
    }
}
