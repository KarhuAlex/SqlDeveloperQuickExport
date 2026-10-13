package dev.quickexport.sqldev;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
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

    private final transient Component owner;
    private final transient Path target;
    private final JLabel status = new JLabel("Running the query\u2026");
    private final JButton cancel = new JButton("Cancel");

    ProgressDialog(Component owner, Path target, ExportFormat format, Runnable onCancel) {
        super(owner instanceof Frame ? (Frame) owner : null, ExportUi.TITLE, false);
        this.owner = owner;
        this.target = target;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        center.add(new JLabel(target.getFileName() + "  (" + format.shortName + ")"), BorderLayout.NORTH);
        center.add(status, BorderLayout.CENTER);
        center.add(bar, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);

        getContentPane().add(center, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        cancel.addActionListener(e -> {
            cancel.setEnabled(false);
            status.setText("Cancelling\u2026");
            onCancel.run();
        });
        setSize(420, 160);
        setLocationRelativeTo(owner);
    }

    @Override
    public void rows(long rowsWritten) {
        if (cancel.isEnabled()) {
            status.setText(NumberFormat.getIntegerInstance().format(rowsWritten) + " rows written\u2026");
        }
    }

    @Override
    public void finished(ExportResult result, long shortenedValues) {
        dispose();
        StringBuilder message = new StringBuilder()
                .append(NumberFormat.getIntegerInstance().format(result.rows())).append(" rows exported to\n")
                .append(target);
        if (result.sheets() > 1) {
            message.append("\n\nThe rows did not fit on one Excel sheet and continue on ")
                    .append(result.sheets()).append(" sheets.");
        }
        if (shortenedValues > 0) {
            message.append("\n\n").append(shortenedValues)
                    .append(" long text value(s) were shortened (Excel's cell limit, or lob.maxChars in the settings file).");
        }
        Object[] options = {"Open folder", "Close"};
        int choice = JOptionPane.showOptionDialog(owner, message.toString(), ExportUi.TITLE,
                JOptionPane.DEFAULT_OPTION,
                shortenedValues > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            openFolder();
        }
    }

    @Override
    public void cancelled() {
        dispose();
        JOptionPane.showMessageDialog(owner, "Export cancelled. No file was written.", ExportUi.TITLE,
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void failed(Throwable error) {
        dispose();
        ExportUi.showError(owner, ErrorMessages.describe(error));
    }

    private void openFolder() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                Desktop.getDesktop().browseFileDirectory(target.toFile());
            } else {
                Desktop.getDesktop().open(target.toAbsolutePath().getParent().toFile());
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not open the folder", e);
            ExportUi.showError(owner, "Could not open the folder:\n" + target.toAbsolutePath().getParent());
        }
    }
}
