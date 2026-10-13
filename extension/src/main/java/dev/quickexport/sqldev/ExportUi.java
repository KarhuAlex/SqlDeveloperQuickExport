package dev.quickexport.sqldev;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.filechooser.FileNameExtensionFilter;

import dev.quickexport.core.ExportResult;
import dev.quickexport.core.ExportSettings;
import oracle.ide.Ide;

/** All Swing for an export: choosing the file, progress and cancel, completion and errors. */
final class ExportUi {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final String TITLE = "Quick Export";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ExportUi() {
    }

    /** Starts an export from the event thread: asks for a file, then runs the job. */
    static void begin(GridSource source, ExportFormat format) {
        Component parent = parentWindow();
        Path target = chooseFile(parent, format);
        if (target == null) {
            return;
        }
        ExportSettings settings = UserConfig.loadSettings();
        ProgressDialog dialog = new ProgressDialog(parent, source, format, target);
        ExportJob job = new ExportJob(source, format, target, settings, dialog);
        dialog.attach(job);
        dialog.setVisible(true);
        job.start();
    }

    // ---------------------------------------------------------------- file choice

    private static Path chooseFile(Component parent, ExportFormat format) {
        JFileChooser chooser = new JFileChooser(UserConfig.lastDirectory().toFile());
        chooser.setDialogTitle(format.menuLabel);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(format.filterDescription, format.extension.substring(1)));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(),
                "export_" + LocalDateTime.now().format(STAMP) + format.extension));

        while (true) {
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(format.extension)) {
                file = new File(file.getParentFile(), file.getName() + format.extension);
            }
            if (!file.exists()) {
                remember(file);
                return file.toPath();
            }
            int answer = JOptionPane.showConfirmDialog(parent,
                    file.getName() + " already exists.\nDo you want to replace it?",
                    TITLE, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                remember(file);
                return file.toPath();
            }
            chooser.setSelectedFile(file); // let the user pick another name
        }
    }

    private static void remember(File file) {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null) {
            UserConfig.rememberDirectory(dir.toPath());
        }
    }

    // ---------------------------------------------------------------- results

    /** Callbacks from the export thread, always delivered on the event thread. */
    interface Progress {
        void rows(long rowsWritten);

        void finished(ExportResult result, long truncatedLobs);

        void cancelled();

        void failed(Throwable error);
    }

    static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.ERROR_MESSAGE);
    }

    /** Turns an exception into a message that says what the user can do about it. */
    static String describe(Throwable t) {
        Throwable root = t;
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException || c instanceof FileSystemException) {
                root = c;
                break;
            }
        }
        if (root instanceof AccessDeniedException) {
            return "You do not have permission to write to:\n" + ((FileSystemException) root).getFile();
        }
        if (root instanceof FileSystemException) {
            FileSystemException fse = (FileSystemException) root;
            String reason = fse.getReason() == null ? "" : fse.getReason();
            if (reason.toLowerCase().contains("another process") || reason.toLowerCase().contains("being used")) {
                return "The file is open in another program, for example Excel.\n"
                        + "Close it and export again:\n" + fse.getFile();
            }
            return "Could not write the file:\n" + fse.getFile() + "\n" + reason;
        }
        if (root instanceof SQLException) {
            return "The query failed:\n" + oneLine(root.getMessage());
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        if (t instanceof IOException) {
            return "The export failed:\n" + msg;
        }
        return "Unexpected error:\n" + t.getClass().getSimpleName() + ": " + msg;
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.trim();
    }

    private static Component parentWindow() {
        try {
            Object window = Ide.getMainWindow();
            return window instanceof Component ? (Component) window : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- progress dialog

    private static final class ProgressDialog extends JDialog implements Progress {

        private static final long serialVersionUID = 1L;

        private final Component owner;
        private final Path target;
        private final JLabel status = new JLabel("Running the query…");
        private final JButton cancel = new JButton("Cancel");
        private ExportJob job;

        ProgressDialog(Component owner, GridSource source, ExportFormat format, Path target) {
            super(owner instanceof java.awt.Frame ? (java.awt.Frame) owner : null, TITLE, false);
            this.owner = owner;
            this.target = target;
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            JLabel file = new JLabel(target.getFileName() + "  (" + format.menuLabel.replace("Quick Export to ", "") + ")");

            JPanel center = new JPanel(new BorderLayout(0, 8));
            center.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
            center.add(file, BorderLayout.NORTH);
            center.add(status, BorderLayout.CENTER);
            center.add(bar, BorderLayout.SOUTH);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.add(cancel);

            getContentPane().add(center, BorderLayout.CENTER);
            getContentPane().add(buttons, BorderLayout.SOUTH);
            cancel.addActionListener(e -> {
                cancel.setEnabled(false);
                status.setText("Cancelling…");
                if (job != null) {
                    job.cancel();
                }
            });
            setSize(420, 160);
            setLocationRelativeTo(owner);
        }

        void attach(ExportJob job) {
            this.job = job;
        }

        @Override
        public void rows(long rowsWritten) {
            if (cancel.isEnabled()) {
                status.setText(NumberFormat.getIntegerInstance().format(rowsWritten) + " rows written…");
            }
        }

        @Override
        public void finished(ExportResult result, long truncatedLobs) {
            dispose();
            showDone(result, truncatedLobs);
        }

        @Override
        public void cancelled() {
            dispose();
            JOptionPane.showMessageDialog(owner, "Export cancelled. No file was written.", TITLE,
                    JOptionPane.INFORMATION_MESSAGE);
        }

        @Override
        public void failed(Throwable error) {
            dispose();
            showError(owner, describe(error));
        }

        private void showDone(ExportResult result, long truncatedLobs) {
            StringBuilder msg = new StringBuilder();
            msg.append(NumberFormat.getIntegerInstance().format(result.rows())).append(" rows exported to\n")
                    .append(target);
            if (result.sheets() > 1) {
                msg.append("\n\nThe rows did not fit on one Excel sheet and continue on ")
                        .append(result.sheets()).append(" sheets.");
            }
            if (truncatedLobs > 0 || result.truncatedCells() > 0) {
                msg.append("\n\n").append(truncatedLobs + result.truncatedCells())
                        .append(" long text value(s) were shortened (see lob.maxChars in the settings file).");
            }
            Object[] options = {"Open folder", "Close"};
            int choice = JOptionPane.showOptionDialog(owner, msg.toString(), TITLE, JOptionPane.DEFAULT_OPTION,
                    truncatedLobs + result.truncatedCells() > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                    null, options, options[0]);
            if (choice == 0) {
                openFolder(target);
            }
        }

        private void openFolder(Path file) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    Desktop.getDesktop().browseFileDirectory(file.toFile());
                } else {
                    Desktop.getDesktop().open(file.toAbsolutePath().getParent().toFile());
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not open the folder", e);
                showError(owner, "Could not open the folder:\n" + file.toAbsolutePath().getParent());
            }
        }
    }
}
