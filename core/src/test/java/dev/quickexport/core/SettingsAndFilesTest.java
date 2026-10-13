package dev.quickexport.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsAndFilesTest {

    @TempDir
    Path dir;

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    @Test
    void defaultsMatchDocumentation() {
        ExportSettings s = ExportSettings.fromProperties(new Properties());
        assertEquals(';', s.delimiter());
        assertEquals('.', s.decimalSeparator());
        assertTrue(s.bom());
        assertEquals(32_000, s.lobMaxChars());
    }

    @Test
    void parsesNamedAndLiteralDelimiters() {
        assertEquals(',', ExportSettings.fromProperties(props("csv.delimiter", "comma")).delimiter());
        assertEquals('\t', ExportSettings.fromProperties(props("csv.delimiter", "tab")).delimiter());
        assertEquals('|', ExportSettings.fromProperties(props("csv.delimiter", " | ")).delimiter());
        assertEquals(';', ExportSettings.fromProperties(props("csv.delimiter", "semicolon")).delimiter());
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        ExportSettings s = ExportSettings.fromProperties(props(
                "csv.delimiter", "toolong", "csv.decimalSeparator", "x", "lob.maxChars", "-5"));
        assertEquals(';', s.delimiter());
        assertEquals('.', s.decimalSeparator());
        assertEquals(32_000, s.lobMaxChars());
    }

    @Test
    void quoteDelimiterIsRejectedByConstructorAndDefaultedFromProperties() {
        assertThrows(IllegalArgumentException.class, () -> new ExportSettings('"', '.', true, 1));
        assertEquals(';', ExportSettings.fromProperties(props("csv.delimiter", "\"")).delimiter());
    }

    @Test
    void delimiterAndDecimalSeparatorNeverCollide() {
        ExportSettings s = ExportSettings.fromProperties(props("csv.delimiter", ",", "csv.decimalSeparator", ","));
        assertEquals(',', s.delimiter());
        assertEquals('.', s.decimalSeparator());
    }

    @Test
    void bomCanBeDisabled() {
        assertFalse(ExportSettings.fromProperties(props("csv.bom", "false")).bom());
    }

    @Test
    void atomicWriteCreatesAndReplacesFile() throws IOException {
        Path target = dir.resolve("out.csv");
        AtomicFiles.write(target, out -> out.write("one".getBytes(StandardCharsets.UTF_8)));
        AtomicFiles.write(target, out -> out.write("two".getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals("two".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
        assertEquals(1, count(dir), "no temp files may be left behind");
    }

    @Test
    void failedWriteKeepsExistingFileAndCleansUp() throws IOException {
        Path target = dir.resolve("out.csv");
        Files.writeString(target, "original");
        assertThrows(IOException.class, () -> AtomicFiles.write(target, out -> {
            out.write("partial".getBytes(StandardCharsets.UTF_8));
            throw new IOException("boom");
        }));
        assertEquals("original", Files.readString(target));
        assertEquals(1, count(dir));
    }

    @Test
    void cancelledWriteLeavesNothing() throws IOException {
        Path target = dir.resolve("new.csv");
        assertThrows(ExportCancelledException.class, () -> AtomicFiles.write(target, out -> {
            throw new ExportCancelledException();
        }));
        assertFalse(Files.exists(target));
        assertEquals(0, count(dir));
    }

    @Test
    void checkWritableAcceptsNewAndExistingFilesWithoutChangingThem() throws IOException {
        Path existing = dir.resolve("a.csv");
        Files.writeString(existing, "keep");
        AtomicFiles.checkWritable(existing);
        AtomicFiles.checkWritable(dir.resolve("brand-new.csv"));
        assertEquals("keep", Files.readString(existing));
        assertEquals(1, count(dir), "probe files must be removed");
    }

    @Test
    void checkWritableRejectsMissingFolder() {
        assertThrows(IOException.class, () -> AtomicFiles.checkWritable(dir.resolve("nope").resolve("x.csv")));
    }

    private static long count(Path d) throws IOException {
        try (Stream<Path> s = Files.list(d)) {
            return s.count();
        }
    }
}
