package dev.quickexport.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class LimitedWriterTest {

    @Test
    void collectsTextUpToTheLimit() throws IOException {
        LimitedWriter w = new LimitedWriter(10);
        w.write("hello");
        w.write(new char[] {' ', 'w', 'o', 'r', 'l', 'd'}, 0, 5);
        assertEquals("hello worl", w.toString());
    }

    @Test
    void refusesToGrowPastTheLimitWithAReadableMessage() throws IOException {
        LimitedWriter w = new LimitedWriter(1_000);
        w.write("x".repeat(1_000));
        IOException e = assertThrows(IOException.class, () -> w.write("y"));
        assertTrue(e.getMessage().contains("clipboard limit") && e.getMessage().contains("Quick Export to CSV"), e.getMessage());
        assertEquals(1_000, w.toString().length(), "nothing is added by the refused write");
    }

    @Test
    void worksAsTheTargetOfTheCsvWriterWithoutAByteOrderMark() throws IOException {
        LimitedWriter w = new LimitedWriter(1_000);
        ExportResult result = new CsvWriter(ExportSettings.defaults()).write(
                ListModel.of(new String[] {"A", "B"}, ListModel.row("x;y", null), ListModel.row(2, "z")), w, ExportControl.NONE);
        assertEquals(2, result.rows());
        assertEquals("A;B\r\n\"x;y\";\r\n2;z\r\n", w.toString());
    }

    @Test
    void aResultTooLargeForTheLimitStopsTheCsvWriter() {
        LimitedWriter w = new LimitedWriter(20);
        assertThrows(IOException.class, () -> new CsvWriter(ExportSettings.defaults()).write(
                ListModel.of(new String[] {"A"}, ListModel.row("0123456789"), ListModel.row("0123456789")), w, ExportControl.NONE));
    }
}
