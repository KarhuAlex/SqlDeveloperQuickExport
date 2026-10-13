package dev.quickexport.core;

import static dev.quickexport.core.ListModel.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class XlsxWriterTest {

    private static final class Written {
        final ExportResult result;
        final XSSFWorkbook wb;

        Written(ExportResult result, XSSFWorkbook wb) {
            this.result = result;
            this.wb = wb;
        }
    }

    private static Written export(XlsxWriter writer, ExportModel model) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportResult r = writer.write(model, out, ExportControl.NONE);
        return new Written(r, new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray())));
    }

    private static Written export(ExportModel model) throws IOException {
        return export(new XlsxWriter(), model);
    }

    @Test
    void headerIsBoldFrozenAndFiltered() throws IOException {
        Written w = export(ListModel.of(new String[] {"ID", "NAME", "WHEN"}, row(1, "a", null)));
        XSSFSheet sheet = w.wb.getSheetAt(0);
        assertEquals("Data", sheet.getSheetName());

        Cell h = sheet.getRow(0).getCell(1);
        assertEquals("NAME", h.getStringCellValue());
        assertTrue(w.wb.getFontAt(h.getCellStyle().getFontIndex()).getBold());

        assertNotNull(sheet.getPaneInformation());
        assertTrue(sheet.getPaneInformation().isFreezePane());
        assertEquals(1, sheet.getPaneInformation().getHorizontalSplitTopRow());

        assertTrue(sheet.getCTWorksheet().isSetAutoFilter());
        assertEquals("A1:C1", sheet.getCTWorksheet().getAutoFilter().getRef());
    }

    @Test
    void valuesKeepTheirTypes() throws IOException {
        ListModel m = ListModel.of(new String[] {"N", "D", "DT", "B", "S", "NUL"},
                row(new BigDecimal("12.50"), LocalDate.of(2024, 1, 31), Timestamp.valueOf("2024-01-31 13:45:10"),
                        true, "text", null));
        Row r = export(m).wb.getSheetAt(0).getRow(1);

        assertEquals(CellType.NUMERIC, r.getCell(0).getCellType());
        assertEquals(12.5, r.getCell(0).getNumericCellValue());

        assertTrue(DateUtil.isCellDateFormatted(r.getCell(1)));
        assertEquals(LocalDate.of(2024, 1, 31).atStartOfDay(), r.getCell(1).getLocalDateTimeCellValue());
        assertEquals("yyyy-mm-dd", r.getCell(1).getCellStyle().getDataFormatString());

        assertTrue(DateUtil.isCellDateFormatted(r.getCell(2)));
        assertEquals(LocalDateTime.of(2024, 1, 31, 13, 45, 10), r.getCell(2).getLocalDateTimeCellValue());
        assertEquals("yyyy-mm-dd hh:mm:ss", r.getCell(2).getCellStyle().getDataFormatString());

        assertEquals(CellType.BOOLEAN, r.getCell(3).getCellType());
        assertTrue(r.getCell(3).getBooleanCellValue());

        assertEquals(CellType.STRING, r.getCell(4).getCellType());
        assertEquals("text", r.getCell(4).getStringCellValue());

        assertNull(r.getCell(5), "NULL must leave the cell absent");
    }

    @Test
    void textThatLooksLikeANumberStaysText() throws IOException {
        Row r = export(ListModel.of(new String[] {"ZIP"}, row("00123"))).wb.getSheetAt(0).getRow(1);
        assertEquals(CellType.STRING, r.getCell(0).getCellType());
        assertEquals("00123", r.getCell(0).getStringCellValue());
    }

    @Test
    void valuesExcelCannotHoldExactlyAreWrittenAsText() throws IOException {
        ListModel m = ListModel.of(new String[] {"BIG", "OLD", "LONGINT"},
                row(new BigDecimal("12345678901234567890.123"), LocalDate.of(1850, 6, 1), 9_007_199_254_740_993L));
        Written w = export(m);
        Row r = w.wb.getSheetAt(0).getRow(1);
        assertEquals("12345678901234567890.123", r.getCell(0).getStringCellValue());
        assertEquals("1850-06-01", r.getCell(1).getStringCellValue());
        assertEquals("9007199254740993", r.getCell(2).getStringCellValue());
    }

    @Test
    void binaryBecomesPlaceholder() throws IOException {
        Row r = export(ListModel.of(new String[] {"X"}, row(new byte[10]))).wb.getSheetAt(0).getRow(1);
        assertEquals("[BINARY 10 bytes]", r.getCell(0).getStringCellValue());
    }

    @Test
    void overlongTextIsTruncatedAndCounted() throws IOException {
        String big = "x".repeat(XlsxWriter.EXCEL_MAX_CELL_CHARS + 500);
        Written w = export(ListModel.of(new String[] {"T"}, row(big), row("ok")));
        assertEquals(1, w.result.truncatedCells());
        assertEquals(XlsxWriter.EXCEL_MAX_CELL_CHARS, w.wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue().length());
    }

    @Test
    void duplicateColumnNamesAreMadeUnique() throws IOException {
        Row h = export(ListModel.of(new String[] {"A", "A", ""}, row(1, 2, 3))).wb.getSheetAt(0).getRow(0);
        assertEquals("A", h.getCell(0).getStringCellValue());
        assertEquals("A_2", h.getCell(1).getStringCellValue());
        assertEquals("COLUMN_3", h.getCell(2).getStringCellValue());
    }

    @Test
    void emptyResultHasHeaderOnly() throws IOException {
        Written w = export(ListModel.of(new String[] {"A", "B"}));
        XSSFSheet s = w.wb.getSheetAt(0);
        assertEquals(0, w.result.rows());
        assertEquals(0, s.getLastRowNum());
        assertEquals("B", s.getRow(0).getCell(1).getStringCellValue());
    }

    @Test
    void zeroColumnsStillProducesAValidWorkbook() throws IOException {
        Written w = export(ListModel.of(new String[0]));
        assertEquals(1, w.wb.getNumberOfSheets());
    }

    @Test
    void columnWidthsAreSensible() throws IOException {
        ListModel m = ListModel.of(new String[] {"A", "B", "C"},
                row("x", "a moderately long value here", "y".repeat(500)));
        XSSFSheet s = export(m).wb.getSheetAt(0);
        int a = s.getColumnWidth(0) / 256;
        int b = s.getColumnWidth(1) / 256;
        int c = s.getColumnWidth(2) / 256;
        assertEquals(8, a, "short columns get the minimum width");
        assertTrue(b > 20 && b < 60);
        assertEquals(60, c, "very wide columns are capped");
    }

    @Test
    void continuesOnANewSheetWhenTheRowLimitIsReached() throws IOException {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            rows.add(row(i));
        }
        // header + 2 data rows per sheet -> 3 sheets for 5 rows
        Written w = export(new XlsxWriter(3), new ListModel(new String[] {"N"}, rows));
        assertEquals(3, w.result.sheets());
        assertEquals(5, w.result.rows());
        assertEquals(3, w.wb.getNumberOfSheets());
        assertEquals("Data (2)", w.wb.getSheetName(1));

        double sum = 0;
        for (int s = 0; s < 3; s++) {
            XSSFSheet sheet = w.wb.getSheetAt(s);
            assertEquals("N", sheet.getRow(0).getCell(0).getStringCellValue(), "header repeated on every sheet");
            assertTrue(sheet.getCTWorksheet().isSetAutoFilter());
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                sum += sheet.getRow(r).getCell(0).getNumericCellValue();
            }
        }
        assertEquals(15.0, sum, "no row may be lost at the sheet boundary");
    }

    @Test
    void cancellationLeavesNoTrailingWork() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(row(i));
        }
        ExportControl cancelled = new ExportControl() {
            @Override
            public boolean isCancelled() {
                return true;
            }
        };
        assertThrows(ExportCancelledException.class,
                () -> new XlsxWriter().write(new ListModel(new String[] {"A"}, rows), new ByteArrayOutputStream(), cancelled));
    }

    @Test
    void resultsWiderThanExcelAllowsFailBeforeWritingAnything() {
        String[] names = new String[XlsxWriter.EXCEL_MAX_COLUMNS + 1];
        java.util.Arrays.fill(names, "C");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOException e = assertThrows(IOException.class,
                () -> new XlsxWriter().write(ListModel.of(names), out, ExportControl.NONE));
        assertTrue(e.getMessage().contains("Export to CSV"), e.getMessage());
        assertEquals(0, out.size());
    }

    @Test
    void rejectsNonsenseSheetLimit() {
        assertThrows(IllegalArgumentException.class, () -> new XlsxWriter(1));
    }

    @Test
    void writesManyRowsStreaming() throws IOException {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            rows.add(row(i, "row " + i));
        }
        Written w = export(new ListModel(new String[] {"N", "S"}, rows));
        assertEquals(20_000, w.result.rows());
        assertEquals(20_000, w.wb.getSheetAt(0).getLastRowNum());
        assertFalse(w.wb.getSheetAt(0).getRow(20_000).getCell(1).getStringCellValue().isEmpty());
    }
}
