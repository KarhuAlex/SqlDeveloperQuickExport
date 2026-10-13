package dev.quickexport.core;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Streams a model to an .xlsx workbook using POI's SXSSF, so only a small window of rows is
 * held in memory regardless of result size.
 *
 * <ul>
 *   <li>Bold header, frozen header row, autofilter, column widths from the header and the
 *       first {@value #WIDTH_SAMPLE_ROWS} rows.</li>
 *   <li>Numbers are numeric cells, dates are date cells (ISO-like display format),
 *       booleans are boolean cells. Numbers Excel cannot hold exactly (more than 15
 *       significant digits) and dates before 1900 are written as text instead of being
 *       silently altered.</li>
 *   <li>Text longer than Excel's 32,767-character cell limit is truncated and counted in
 *       {@link ExportResult#truncatedCells()}.</li>
 *   <li>When a sheet reaches Excel's row limit the export continues on a new sheet, so
 *       no rows are dropped.</li>
 * </ul>
 */
public final class XlsxWriter {

    public static final int EXCEL_MAX_ROWS = 1_048_576;
    public static final int EXCEL_MAX_COLUMNS = 16_384;
    public static final int EXCEL_MAX_CELL_CHARS = 32_767;

    static final int WIDTH_SAMPLE_ROWS = 1000;
    private static final int MIN_WIDTH = 8;
    private static final int MAX_WIDTH = 60;
    private static final long PROGRESS_EVERY = 1000;
    private static final LocalDate EXCEL_EPOCH_SAFE = LocalDate.of(1900, 3, 1);

    private final int maxRowsPerSheet;

    public XlsxWriter() {
        this(EXCEL_MAX_ROWS);
    }

    /** Package-private so tests can exercise sheet rollover without a million rows. */
    XlsxWriter(int maxRowsPerSheet) {
        if (maxRowsPerSheet < 2) {
            throw new IllegalArgumentException("maxRowsPerSheet must be >= 2");
        }
        this.maxRowsPerSheet = maxRowsPerSheet;
    }

    /** Writes the workbook to {@code out}; the stream is not closed. */
    public ExportResult write(ExportModel model, OutputStream out, ExportControl control) throws IOException {
        if (model.getColumnCount() > EXCEL_MAX_COLUMNS) {
            throw new IOException("The result has " + model.getColumnCount() + " columns, but an Excel sheet holds at most "
                    + EXCEL_MAX_COLUMNS + ". Export to CSV instead.");
        }
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        wb.setCompressTempFiles(true);
        try {
            Styles styles = new Styles(wb);
            int cols = model.getColumnCount();
            String[] header = HeaderNames.unique(model);

            SheetState sheet = newSheet(wb, styles, header, 1);
            int sheets = 1;
            long rows = 0;
            long truncated = 0;

            try (ExportModel.RowCursor cursor = model.openRows()) {
                while (cursor.next()) {
                    if (control.isCancelled()) {
                        throw new ExportCancelledException();
                    }
                    if (sheet.nextRow >= maxRowsPerSheet) {
                        finish(sheet, cols);
                        sheet = newSheet(wb, styles, header, ++sheets);
                    }
                    Row row = sheet.sheet.createRow(sheet.nextRow++);
                    for (int c = 0; c < cols; c++) {
                        Object v = cursor.getValue(c);
                        int len = writeCell(row, c, v, styles);
                        if (len < 0) {
                            truncated++;
                            len = EXCEL_MAX_CELL_CHARS;
                        }
                        if (sheet.nextRow <= WIDTH_SAMPLE_ROWS + 1) {
                            sheet.widths[c] = Math.max(sheet.widths[c], len);
                        }
                    }
                    if (++rows % PROGRESS_EVERY == 0) {
                        control.progress(rows);
                    }
                }
            }
            finish(sheet, cols);
            control.progress(rows);
            wb.write(out);
            return new ExportResult(rows, truncated, sheets);
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    private SheetState newSheet(SXSSFWorkbook wb, Styles styles, String[] header, int index) {
        SXSSFSheet sheet = wb.createSheet(index == 1 ? "Data" : "Data (" + index + ")");
        int[] widths = new int[header.length];
        Row hr = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) {
            Cell cell = hr.createCell(c);
            cell.setCellValue(truncate(header[c]));
            cell.setCellStyle(styles.header);
            widths[c] = header[c].length();
        }
        sheet.createFreezePane(0, 1);
        return new SheetState(sheet, widths);
    }

    private void finish(SheetState s, int cols) {
        if (cols == 0) {
            return;
        }
        for (int c = 0; c < cols; c++) {
            int chars = Math.min(Math.max(s.widths[c] + 2, MIN_WIDTH), MAX_WIDTH);
            s.sheet.setColumnWidth(c, chars * 256);
        }
        s.sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, cols - 1));
    }

    /**
     * Writes one value. Returns the approximate display length for column sizing, or -1 if the
     * text had to be truncated to Excel's cell limit.
     */
    private static int writeCell(Row row, int col, Object v, Styles styles) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Boolean b) {
            row.createCell(col).setCellValue(b);
            return 5;
        }
        if (v instanceof Number n) {
            if (isExactInExcel(n)) {
                // Float.doubleValue() would show 0.1f as 0.10000000149...; go via its decimal text.
                double x = n instanceof Float ? Double.parseDouble(n.toString()) : n.doubleValue();
                row.createCell(col).setCellValue(x);
                return ValueText.text(n, '.').length();
            }
            return writeText(row, col, ValueText.text(n, '.'));
        }
        LocalDate d = ValueText.asDate(v);
        if (d != null) {
            return writeDate(row, col, d.atStartOfDay(), styles.date, v);
        }
        LocalDateTime dt = ValueText.asDateTime(v);
        if (dt == null) {
            dt = zonedWallClock(v);
        }
        if (dt != null) {
            return writeDate(row, col, dt, styles.dateTime, v);
        }
        return writeText(row, col, ValueText.text(v, '.'));
    }

    private static int writeDate(Row row, int col, LocalDateTime dt, CellStyle style, Object original) {
        if (dt.toLocalDate().isBefore(EXCEL_EPOCH_SAFE)) {
            return writeText(row, col, ValueText.text(original, '.'));
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(dt);
        cell.setCellStyle(style);
        return style.getDataFormatString().length();
    }

    /** Excel has no time zones: keep the wall-clock time the value was recorded with. */
    private static LocalDateTime zonedWallClock(Object v) {
        if (v instanceof OffsetDateTime o) {
            return o.toLocalDateTime();
        }
        if (v instanceof ZonedDateTime z) {
            return z.toLocalDateTime();
        }
        if (v instanceof Instant i) {
            return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
        }
        return null;
    }

    private static int writeText(Row row, int col, String s) {
        boolean cut = s.length() > EXCEL_MAX_CELL_CHARS;
        row.createCell(col).setCellValue(cut ? s.substring(0, EXCEL_MAX_CELL_CHARS) : s);
        return cut ? -1 : longestLine(s);
    }

    private static int longestLine(String s) {
        int best = 0;
        int cur = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n' || ch == '\r') {
                best = Math.max(best, cur);
                cur = 0;
            } else {
                cur++;
            }
        }
        return Math.max(best, cur);
    }

    private static String truncate(String s) {
        return s.length() > EXCEL_MAX_CELL_CHARS ? s.substring(0, EXCEL_MAX_CELL_CHARS) : s;
    }

    /** Excel stores doubles: exact for integers up to 2^53 and decimals up to 15 significant digits. */
    private static boolean isExactInExcel(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double x = n.doubleValue();
            return !Double.isNaN(x) && !Double.isInfinite(x);
        }
        if (n instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().precision() <= 15;
        }
        if (n instanceof BigInteger bi) {
            return bi.bitLength() <= 53;
        }
        if (n instanceof Long l) {
            return Math.abs(l) <= (1L << 53);
        }
        return true;
    }

    private static final class SheetState {
        final SXSSFSheet sheet;
        final int[] widths;
        int nextRow = 1;

        SheetState(SXSSFSheet sheet, int[] widths) {
            this.sheet = sheet;
            this.widths = widths;
        }
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle date;
        final CellStyle dateTime;

        Styles(SXSSFWorkbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            header = wb.createCellStyle();
            header.setFont(bold);

            short dateFmt = wb.createDataFormat().getFormat("yyyy-mm-dd");
            short dateTimeFmt = wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss");
            date = wb.createCellStyle();
            date.setDataFormat(dateFmt);
            dateTime = wb.createCellStyle();
            dateTime.setDataFormat(dateTimeFmt);
        }
    }
}
