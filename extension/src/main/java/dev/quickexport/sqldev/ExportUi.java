package dev.quickexport.sqldev;

import java.awt.Component;
import java.nio.file.Path;

import javax.swing.JOptionPane;

import dev.quickexport.core.ExportSettings;
import oracle.ide.Ide;

/** Entry points for an export started from the menu (event thread): output, progress, outcome. */
final class ExportUi {

    static final String TITLE = "Quick Export";

    private ExportUi() {
    }

    /** Asks where to save the file, then exports to it. */
    static void beginFile(GridSource source, ExportFormat format) {
        Component parent = parentWindow();
        Path target = SaveDialog.choose(parent, format);
        if (target == null) {
            return;
        }
        ExportSettings settings = UserConfig.loadSettings();
        run(parent, source, new FileOutput(format, target, settings, source.sql()), settings);
    }

    static void beginClipboard(GridSource source) {
        ExportSettings settings = UserConfig.loadSettings();
        run(parentWindow(), source, new ClipboardOutput(settings), settings);
    }

    static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private static void run(Component parent, GridSource source, ExportOutput output, ExportSettings settings) {
        ExportJob job = new ExportJob(source, output, settings);
        ProgressDialog dialog = new ProgressDialog(parent, output, job::cancel);
        dialog.setVisible(true);
        job.start(dialog);
    }

    private static Component parentWindow() {
        try {
            Object window = Ide.getMainWindow();
            return window instanceof Component ? (Component) window : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
