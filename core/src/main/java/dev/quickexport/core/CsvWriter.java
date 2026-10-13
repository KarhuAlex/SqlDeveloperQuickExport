package dev.quickexport.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Streams a model to RFC 4180-style CSV: UTF-8 (optionally with BOM so Excel detects the
 * encoding), CRLF line ends, fields quoted when they contain the delimiter, a quote or a
 * line break, NULL as an empty field, ISO dates.
 */
public final class CsvWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final long PROGRESS_EVERY = 1000;

    private final ExportSettings settings;

    public CsvWriter(ExportSettings settings) {
        this.settings = settings;
    }

    /** Writes everything to {@code out}; the stream is flushed but not closed. */
    public ExportResult write(ExportModel model, OutputStream out, ExportControl control) throws IOException {
        if (settings.bom()) {
            out.write(BOM);
        }
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
        int cols = model.getColumnCount();
        char delimiter = settings.delimiter();

        String[] header = HeaderNames.unique(model);
        for (int c = 0; c < cols; c++) {
            if (c > 0) {
                w.write(delimiter);
            }
            writeField(w, header[c], delimiter);
        }
        w.write("\r\n");

        long rows = 0;
        try (ExportModel.RowCursor cursor = model.openRows()) {
            while (cursor.next()) {
                if (control.isCancelled()) {
                    throw new ExportCancelledException();
                }
                for (int c = 0; c < cols; c++) {
                    if (c > 0) {
                        w.write(delimiter);
                    }
                    writeField(w, ValueText.text(cursor.getValue(c), settings.decimalSeparator()), delimiter);
                }
                w.write("\r\n");
                if (++rows % PROGRESS_EVERY == 0) {
                    control.progress(rows);
                }
            }
        }
        w.flush();
        control.progress(rows);
        return new ExportResult(rows, 0, 1);
    }

    private static void writeField(Writer w, String s, char delimiter) throws IOException {
        if (!needsQuoting(s, delimiter)) {
            w.write(s);
            return;
        }
        w.write('"');
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                w.write(s, start, i - start + 1);
                w.write('"');
                start = i + 1;
            }
        }
        w.write(s, start, s.length() - start);
        w.write('"');
    }

    private static boolean needsQuoting(String s, char delimiter) {
        int n = s.length();
        if (n == 0) {
            return false;
        }
        if (s.charAt(0) == ' ' || s.charAt(n - 1) == ' ') {
            return true;
        }
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (ch == delimiter || ch == '"' || ch == '\r' || ch == '\n') {
                return true;
            }
        }
        return false;
    }
}
