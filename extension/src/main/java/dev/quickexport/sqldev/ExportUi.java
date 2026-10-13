package dev.quickexport.sqldev;

import java.awt.Component;
import java.nio.file.Path;

import javax.swing.JOptionPane;

import oracle.ide.Ide;

/** Entry point for an export started from the menu (event thread): file, progress, outcome. */
final class ExportUi {

    static final String TITLE = "Quick Export";

    private ExportUi() {
    }

    static void begin(GridSource source, ExportFormat format) {
        Component parent = parentWindow();
        Path target = SaveDialog.choose(parent, format);
        if (target == null) {
            return;
        }
        ExportJob job = new ExportJob(source, format, target, UserConfig.loadSettings());
        ProgressDialog dialog = new ProgressDialog(parent, target, format, job::cancel);
        dialog.setVisible(true);
        job.start(dialog);
    }

    static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.ERROR_MESSAGE);
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
