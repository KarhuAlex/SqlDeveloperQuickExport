package dev.quickexport.sqldev;

import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Asks where to save the export, starting in the last folder used. */
final class SaveDialog {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private SaveDialog() {
    }

    /** Returns the chosen file, or {@code null} if the user cancelled. */
    static Path choose(Component parent, ExportFormat format) {
        JFileChooser chooser = new JFileChooser(UserConfig.lastDirectory().toFile());
        chooser.setDialogTitle(format.menuLabel);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(format.filterDescription, format.extension.substring(1)));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(),
                "export_" + LocalDateTime.now().format(STAMP) + format.extension));

        while (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = withExtension(chooser.getSelectedFile(), format);
            if (!file.exists() || confirmReplace(parent, file)) {
                UserConfig.rememberDirectory(file.getAbsoluteFile().getParentFile().toPath());
                return file.toPath();
            }
            chooser.setSelectedFile(file); // let the user pick another name
        }
        return null;
    }

    private static File withExtension(File file, ExportFormat format) {
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(format.extension)) {
            return file;
        }
        return new File(file.getParentFile(), file.getName() + format.extension);
    }

    private static boolean confirmReplace(Component parent, File file) {
        return JOptionPane.showConfirmDialog(parent, file.getName() + " already exists.\nDo you want to replace it?",
                ExportUi.TITLE, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
