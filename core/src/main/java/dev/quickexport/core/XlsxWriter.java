package dev.quickexport.core;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
 *   <li>Numbers are numeric cells, dates are date cells, booleans are boolean cells. Numbers
 *       Excel cannot hold exactly (more than 15 significant digits) and dates before 1900 are
 *       written as text instead of being silently altered.</li>
 *   <li>Text longer than Excel's 32,767-character cell limit is truncated and counted in
 *       {@link ExportResult#truncatedCells()}.</li>
 *   <li>When a sheet reaches Excel's row limit the export continues on a new sheet, so
 *       no rows are dropped.</li>
 *   <li>If a query is given, it goes on a second worksheet named "Query", one line per row.
 *       Cells hold plain text, so a line starting with {@code =} is never evaluated.</li>
 * </ul>
 */
public final class XlsxWriter {

    public static final int EXCEL_MAX_ROWS = 1_048_576;
    public static final int EXCEL_MAX_COLUMNS = 16_384;
    public static final int EXCEL_MAX_CELL_CHARS = 32_767;

    static final int WIDTH_SAMPLE_ROWS = 1000;

    private static final int ROW_WINDOW = 100;
    private static final int MIN_WIDTH = 8;
    private static final int MAX_WIDTH = 60;
    private static final String QUERY_SHEET = "Query";
    private static final int QUERY_COLUMN_CHARS = 100;
    private static final int DATE_CHARS = 10;
    private static final int DATE_TIME_CHARS = 19;
    private static final int BOOLEAN_CHARS = 5;
    private static final int TRUNCATED = -1;
    private static final LocalDate EARLIEST_EXCEL_DATE = LocalDate.of(1900, 3, 1);

    private final int maxRowsPerSheet;
    private final String query;

    public XlsxWriter() {
        this(null);
    }

    /**
     * @param query SQL text to put on a second worksheet named "Query", one line per row;
     *              {@code null} or blank for no such worksheet
     */
    public XlsxWriter(String query) {
        this(EXCEL_MAX_ROWS, query);
    }

    /** Package-private so tests can exercise sheet rollover without a million rows. */
    XlsxWriter(int maxRowsPerSheet, String query) {
        if (maxRowsPerSheet < 2) {
            throw new IllegalArgumentException("maxRowsPerSheet must be >= 2");
        }
        this.maxRowsPerSheet = maxRowsPerSheet;
        this.query = query == null || query.isBlank() ? null : query.strip();
    }

    /** Writes the workbook to {@code out}; the stream is not closed. */
    public ExportResult write(ExportModel model, OutputStream out, ExportControl control) throws IOException {
        int cols = model.getColumnCount();
        if (cols > EXCEL_MAX_COLUMNS) {
            throw new IOException("The result has " + cols + " columns, but an Excel sheet holds at most "
                    + EXCEL_MAX_COLUMNS + ". Export to CSV instead.");
        }
        RowTicker ticker = new RowTicker(control);
        SXSSFWorkbook wb = new SXSSFWorkbook(ROW_WINDOW);
        wb.setCompressTempFiles(true);
        try {
            Styles styles = new Styles(wb);
            String[] header = HeaderNames.unique(model);
            SheetState sheet = newSheet(wb, styles, header, 1);
            int sheets = 1;
            // Created right after the first data sheet so it stays the second sheet even when
            // the data rolls over onto further sheets.
            long truncated = query == null ? 0 : writeQuerySheet(wb);

            try (ExportModel.RowCursor cursor = model.openRows()) {
                while (cursor.next()) {
                    ticker.beforeRow();
                    if (sheet.nextRow >= maxRowsPerSheet) {
                        finish(sheet, cols);
                        sheet = newSheet(wb, styles, header, ++sheets);
                    }
                    boolean measure = sheet.nextRow <= WIDTH_SAMPLE_ROWS;
                    Row row = sheet.sheet.createRow(sheet.nextRow++);
                    for (int c = 0; c < cols; c++) {
                        int chars = writeCell(row, c, cursor.getValue(c), styles, measure);
                        if (chars == TRUNCATED) {
                            truncated++;
                            chars = EXCEL_MAX_CELL_CHARS;
                        }
                        if (measure) {
                            sheet.widths[c] = Math.max(sheet.widths[c], chars);
                        }
                    }
                    ticker.afterRow();
                }
            }
            finish(sheet, cols);
            long rows = ticker.finish();
            wb.write(out);
            return new ExportResult(rows, truncated, sheets);
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    private static SheetState newSheet(SXSSFWorkbook wb, Styles styles, String[] header, int index) {
        SXSSFSheet sheet = wb.createSheet(index == 1 ? "Data" : "Data (" + index + ")");
        int[] widths = new int[header.length];
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(truncate(header[c]));
            cell.setCellStyle(styles.header);
            widths[c] = header[c].length();
        }
        sheet.createFreezePane(0, 1);
        return new SheetState(sheet, widths);
    }

    /** Writes the query one line per row and returns how many lines had to be cut to the cell limit. */
    private long writeQuerySheet(SXSSFWorkbook wb) {
        SXSSFSheet sheet = wb.createSheet(QUERY_SHEET);
        sheet.setColumnWidth(0, QUERY_COLUMN_CHARS * 256);
        long truncated = 0;
        int rowIndex = 0;
        for (String line : query.split("\r\n|\r|\n", -1)) {
            Row row = sheet.createRow(rowIndex++);
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() > EXCEL_MAX_CELL_CHARS) {
                truncated++;
            }
            row.createCell(0).setCellValue(truncate(line));
        }
        return truncated;
    }

    private static void finish(SheetState s, int cols) {
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
     * Writes one value. Returns its approximate display width in characters when {@code measure}
     * is set (0 otherwise), or {@link #TRUNCATED} if the text had to be cut to Excel's cell limit.
     */
    private static int writeCell(Row row, int col, Object v, Styles styles, boolean measure) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Boolean b) {
            row.createCell(col).setCellValue(b);
            return BOOLEAN_CHARS;
        }
        if (v instanceof Number n) {
            if (!isExactInExcel(n)) {
                return writeText(row, col, ValueText.text(n, '.'), measure);
            }
            // Parse the decimal text: Float.doubleValue() gives 0.10000000149 for 0.1f.
            row.createCell(col).setCellValue(n instanceof Float ? Double.parseDouble(n.toString()) : n.doubleValue());
            return measure ? ValueText.text(n, '.').length() : 0;
        }
        LocalDate date = ValueText.asDate(v);
        if (date != null) {
            return writeDate(row, col, date.atStartOfDay(), styles.date, DATE_CHARS, v, measure);
        }
        LocalDateTime dateTime = ValueText.asDateTime(v);
        if (dateTime == null) {
            dateTime = wallClock(v);
        }
        if (dateTime != null) {
            return writeDate(row, col, dateTime, styles.dateTime, DATE_TIME_CHARS, v, measure);
        }
        return writeText(row, col, ValueText.text(v, '.'), measure);
    }

    private static int writeDate(Row row, int col, LocalDateTime value, CellStyle style, int chars,
            Object original, boolean measure) {
        if (value.toLocalDate().isBefore(EARLIEST_EXCEL_DATE)) {
            return writeText(row, col, ValueText.text(original, '.'), measure);
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return chars;
    }

    /** Excel has no time zones: keep the wall-clock time the value was recorded with. */
    private static LocalDateTime wallClock(Object v) {
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

    private static int writeText(Row row, int col, String s, boolean measure) {
        boolean cut = s.length() > EXCEL_MAX_CELL_CHARS;
        row.createCell(col).setCellValue(cut ? s.substring(0, EXCEL_MAX_CELL_CHARS) : s);
        if (cut) {
            return TRUNCATED;
        }
        return measure ? longestLine(s) : 0;
    }

    private static int longestLine(String s) {
        int best = 0;
        int current = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n' || ch == '\r') {
                best = Math.max(best, current);
                current = 0;
            } else {
                current++;
            }
        }
        return Math.max(best, current);
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

            date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            dateTime = wb.createCellStyle();
            dateTime.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        }
    }
}
