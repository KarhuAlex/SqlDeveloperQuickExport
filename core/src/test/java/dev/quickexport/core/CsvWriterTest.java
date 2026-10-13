package dev.quickexport.core;

import static dev.quickexport.core.ListModel.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvWriterTest {

    private static final String BOM = "\uFEFF";

    private static String csv(ExportSettings settings, ExportModel model) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CsvWriter(settings).write(model, out, ExportControl.NONE);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String csv(ExportModel model) throws IOException {
        return csv(ExportSettings.defaults(), model);
    }

    @Test
    void writesBomHeaderAndRowsWithSemicolonAndCrlf() throws IOException {
        ListModel m = ListModel.of(new String[] {"ID", "NAME"}, row(1, "a"), row(2, "b"));
        assertEquals(BOM + "ID;NAME\r\n1;a\r\n2;b\r\n", csv(m));
    }

    @Test
    void bomIsExactlyThreeUtf8Bytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CsvWriter(ExportSettings.defaults()).write(ListModel.of(new String[] {"A"}), out, ExportControl.NONE);
        byte[] b = out.toByteArray();
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, new byte[] {b[0], b[1], b[2]});
    }

    @Test
    void canOmitBomAndUseOtherDelimiter() throws IOException {
        ExportSettings s = new ExportSettings(',', '.', false, 100);
        assertEquals("A,B\r\n1,x\r\n", csv(s, ListModel.of(new String[] {"A", "B"}, row(1, "x"))));
    }

    @Test
    void quotesAndEscapesWhereNeeded() throws IOException {
        ListModel m = ListModel.of(new String[] {"V"},
                row("plain"), row("a;b"), row("say \"hi\""), row("line1\nline2"), row("cr\rx"),
                row(" lead"), row("trail "), row("\"q\""));
        assertEquals(BOM + "V\r\nplain\r\n\"a;b\"\r\n\"say \"\"hi\"\"\"\r\n\"line1\nline2\"\r\n\"cr\rx\"\r\n"
                + "\" lead\"\r\n\"trail \"\r\n\"\"\"q\"\"\"\r\n", csv(m));
    }

    @Test
    void commaIsNotQuotedWhenDelimiterIsSemicolon() throws IOException {
        assertEquals(BOM + "V\r\na,b\r\n", csv(ListModel.of(new String[] {"V"}, row("a,b"))));
    }

    @Test
    void headerNamesAreEscapedToo() throws IOException {
        assertEquals(BOM + "\"a;b\"\r\n", csv(ListModel.of(new String[] {"a;b"})));
    }

    @Test
    void nullIsEmptyField() throws IOException {
        assertEquals(BOM + "A;B;C\r\n;x;\r\n", csv(ListModel.of(new String[] {"A", "B", "C"}, row(null, "x", null))));
    }

    @Test
    void nordicCharactersSurviveUtf8() throws IOException {
        String text = "Äidinkieli Ö å Ø – €";
        assertEquals(BOM + "N\r\n" + text + "\r\n", csv(ListModel.of(new String[] {"N"}, row(text))));
    }

    @Test
    void datesAreIso() throws IOException {
        ListModel m = ListModel.of(new String[] {"D", "T", "T2", "SD", "LDT", "ODT"},
                row(LocalDate.of(2024, 1, 31),
                        Timestamp.valueOf("2024-01-31 13:45:00"),
                        Timestamp.valueOf("2024-01-31 13:45:00.125"),
                        java.sql.Date.valueOf("2024-02-29"),
                        LocalDateTime.of(2024, 3, 1, 0, 0, 0),
                        OffsetDateTime.of(2024, 3, 1, 10, 0, 0, 0, ZoneOffset.ofHours(2))));
        assertEquals(BOM + "D;T;T2;SD;LDT;ODT\r\n"
                + "2024-01-31;2024-01-31 13:45:00;2024-01-31 13:45:00.125;2024-02-29;2024-03-01 00:00:00;"
                + "2024-03-01T10:00:00+02:00\r\n", csv(m));
    }

    @Test
    void numbersArePlainAndHonourDecimalSeparator() throws IOException {
        ListModel m = ListModel.of(new String[] {"N"},
                row(new BigDecimal("1234567890123456789.50")), row(1.0E10), row(0.1f), row(42L), row(Double.NaN),
                row(new BigDecimal("1E+3")));
        assertEquals(BOM + "N\r\n1234567890123456789.50\r\n10000000000\r\n0.1\r\n42\r\nNaN\r\n1000\r\n", csv(m));

        ExportSettings comma = new ExportSettings(';', ',', true, 100);
        assertEquals(BOM + "N\r\n1,5\r\n", csv(comma, ListModel.of(new String[] {"N"}, row(new BigDecimal("1.5")))));
    }

    @Test
    void booleansAndBinaryPlaceholder() throws IOException {
        ListModel m = ListModel.of(new String[] {"B", "X"}, row(true, new byte[] {1, 2, 3}));
        assertEquals(BOM + "B;X\r\ntrue;[BINARY 3 bytes]\r\n", csv(m));
    }

    @Test
    void duplicateAndBlankColumnNamesBecomeUnique() throws IOException {
        ListModel m = ListModel.of(new String[] {"ID", "id", "ID", "", null, "ID_2"}, row(1, 2, 3, 4, 5, 6));
        // The real "ID_2" column keeps its name; generated suffixes skip it.
        assertEquals(BOM + "ID;id_3;ID_4;COLUMN_4;COLUMN_5;ID_2\r\n1;2;3;4;5;6\r\n", csv(m));
    }

    @Test
    void emptyResultWritesOnlyHeader() throws IOException {
        assertEquals(BOM + "A;B\r\n", csv(ListModel.of(new String[] {"A", "B"})));
    }

    @Test
    void zeroColumnsWritesJustALineBreak() throws IOException {
        assertEquals(BOM + "\r\n", csv(ListModel.of(new String[0])));
    }

    @Test
    void reportsRowCount() throws IOException {
        ListModel m = ListModel.of(new String[] {"A"}, row(1), row(2), row(3));
        ExportResult r = new CsvWriter(ExportSettings.defaults()).write(m, new ByteArrayOutputStream(), ExportControl.NONE);
        assertEquals(3, r.rows());
        assertEquals(1, r.sheets());
    }

    @Test
    void cancellationStopsWithExceptionAndPollsPerRow() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            rows.add(row(i));
        }
        ListModel m = new ListModel(new String[] {"A"}, rows);
        int[] polls = {0};
        ExportControl control = new ExportControl() {
            @Override
            public boolean isCancelled() {
                return ++polls[0] > 10;
            }
        };
        assertThrows(ExportCancelledException.class,
                () -> new CsvWriter(ExportSettings.defaults()).write(m, new ByteArrayOutputStream(), control));
        assertEquals(11, polls[0]);
    }

    @Test
    void progressIsReported() throws IOException {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            rows.add(row(i));
        }
        List<Long> seen = new ArrayList<>();
        ExportControl control = new ExportControl() {
            @Override
            public void progress(long rowsWritten) {
                seen.add(rowsWritten);
            }
        };
        new CsvWriter(ExportSettings.defaults()).write(new ListModel(new String[] {"A"}, rows),
                new ByteArrayOutputStream(), control);
        assertTrue(seen.contains(1000L) && seen.contains(2000L));
        assertEquals(2500L, seen.get(seen.size() - 1));
    }
}
