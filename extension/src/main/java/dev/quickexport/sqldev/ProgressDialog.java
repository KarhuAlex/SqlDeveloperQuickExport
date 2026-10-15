package dev.quickexport.sqldev;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import dev.quickexport.core.ErrorMessages;
import dev.quickexport.core.ExportResult;

/** Shows a running export with a Cancel button, then reports how it ended. */
final class ProgressDialog extends JDialog implements ExportJob.Listener {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final int BAR_STEPS = 1000;

    private final transient Component owner;
    private final transient Optional<Path> file;
    private final JLabel status = new JLabel("Running the query…");
    private final JButton cancel = new JButton("Cancel");
    private final JProgressBar bar = new JProgressBar(0, BAR_STEPS);
    private long written;
    private long total = -1;
    private boolean ended;

    ProgressDialog(Component owner, ExportOutput output, Runnable onCancel) {
        super(owner instanceof Frame ? (Frame) owner : null, ExportUi.TITLE, false);
        this.owner = owner;
        this.file = output.file();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        bar.setIndeterminate(true);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        center.add(new JLabel(output.label()), BorderLayout.NORTH);
        center.add(status, BorderLayout.CENTER);
        center.add(bar, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);

        getContentPane().add(center, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        cancel.addActionListener(e -> {
            cancel.setEnabled(false);
            status.setText("Cancelling…");
            onCancel.run();
        });
        setSize(420, 160);
        setLocationRelativeTo(owner);
    }

    @Override
    public void rows(long rowsWritten) {
        written = rowsWritten;
        showProgress();
    }

    @Override
    public void total(long totalRows) {
        if (!ended && totalRows > 0) {
            total = totalRows;
            showProgress();
        }
    }

    /** Shows "written / total (percent)" while the total is known and not exceeded, otherwise just the count. */
    private void showProgress() {
        if (ended || !cancel.isEnabled()) { // after Cancel the label keeps saying "Cancelling"
            return;
        }
        NumberFormat count = NumberFormat.getIntegerInstance();
        if (total > 0 && written <= total) {
            double fraction = (double) written / total;
            bar.setIndeterminate(false);
            bar.setValue((int) Math.round(fraction * BAR_STEPS));
            status.setText(count.format(written) + " / " + count.format(total) + " rows ("
                    + NumberFormat.getPercentInstance().format(fraction) + ")");
        } else {
            bar.setIndeterminate(true);
            status.setText(count.format(written) + " rows written…");
        }
    }

    @Override
    public void finished(ExportResult result, long shortenedValues) {
        ended = true;
        dispose();
        String rows = NumberFormat.getIntegerInstance().format(result.rows());
        StringBuilder notices = new StringBuilder();
        if (result.sheets() > 1) {
            notices.append("\n\nThe rows did not fit on one Excel sheet, so they continue on ")
                    .append(result.sheets()).append(" sheets.");
        }
        if (shortenedValues > 0) {
            notices.append("\n\n").append(shortenedValues)
                    .append(" long text value(s) were shortened to fit Excel's cell limit or the lob.maxChars setting.");
        }
        int type = shortenedValues > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE;

        if (file.isEmpty()) {
            JOptionPane.showMessageDialog(owner, rows + " rows copied to the clipboard." + notices, ExportUi.TITLE, type);
            return;
        }
        Object[] options = {"Open file", "Open folder", "Close"};
        int choice = JOptionPane.showOptionDialog(owner, rows + " rows exported to\n" + file.get() + notices,
                ExportUi.TITLE, JOptionPane.DEFAULT_OPTION, type, null, options, options[0]);
        if (choice == 0) {
            openFile(file.get());
        } else if (choice == 1) {
            openFolder(file.get());
        }
    }

    @Override
    public void cancelled() {
        ended = true;
        dispose();
        String message = file.isPresent()
                ? "Export cancelled. No file was written."
                : "Copy cancelled. The clipboard was not changed.";
        JOptionPane.showMessageDialog(owner, message, ExportUi.TITLE, JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void failed(Throwable error) {
        ended = true;
        dispose();
        ExportUi.showError(owner, ErrorMessages.describe(error));
    }

    private void openFile(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not open the file", e);
            ExportUi.showError(owner, "Could not open the file:\n" + path
                    + "\nCheck that a program is set up to open this file type.");
        }
    }

    private void openFolder(Path path) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                Desktop.getDesktop().browseFileDirectory(path.toFile());
            } else {
                Desktop.getDesktop().open(path.toAbsolutePath().getParent().toFile());
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not open the folder", e);
            ExportUi.showError(owner, "Could not open the folder:\n" + path.toAbsolutePath().getParent());
        }
    }
}
