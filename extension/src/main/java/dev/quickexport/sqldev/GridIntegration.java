package dev.quickexport.sqldev;

import oracle.dbtools.raptor.controls.grid.RaptorGridTable;

/** Registers our menu item with the result grid. All references to grid internals start here. */
final class GridIntegration {

    private GridIntegration() {
    }

    static void install() {
        RaptorGridTable.addGridContextMenu(QuickExportMenuItem.getInstance());
    }
}
