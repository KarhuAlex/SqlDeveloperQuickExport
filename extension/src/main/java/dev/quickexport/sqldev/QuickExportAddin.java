package dev.quickexport.sqldev;

import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.ide.Addin;

/**
 * Entry point declared in META-INF/extension.xml. It deliberately touches no result-grid
 * class itself: everything that depends on SQL Developer internals lives in
 * {@link GridIntegration}, so a version mismatch is caught here and logged instead of
 * breaking IDE start-up.
 */
public final class QuickExportAddin implements Addin {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");

    @Override
    public void initialize() {
        String status;
        try {
            GridIntegration.install();
            LOG.info("Quick Export: result grid menu items installed");
            status = "installed";
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Quick Export is disabled: could not attach to this SQL Developer version", t);
            status = "FAILED: " + t;
        }
        spikeMarker(status); // SPIKE ONLY: remove before release
    }

    private static void spikeMarker(String status) {
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "quickexport-addin-loaded.txt"),
                    status + System.lineSeparator());
        } catch (Exception ignored) {
            // diagnostics only
        }
    }
}
