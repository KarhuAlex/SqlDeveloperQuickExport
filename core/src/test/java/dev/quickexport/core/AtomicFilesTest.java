package dev.quickexport.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {

    @TempDir
    Path dir;

    @Test
    void createsAndReplacesTheTargetAndReturnsTheBodyResult() throws IOException {
        Path target = dir.resolve("out.csv");
        String result = AtomicFiles.write(target, out -> {
            out.write("one".getBytes(StandardCharsets.UTF_8));
            return "body result";
        });
        assertEquals("body result", result);
        AtomicFiles.write(target, out -> {
            out.write("two".getBytes(StandardCharsets.UTF_8));
            return null;
        });
        assertEquals("two", Files.readString(target));
        assertEquals(1, fileCount(), "no temp files may be left behind");
    }

    @Test
    void aFailedWriteKeepsTheExistingFileAndCleansUp() throws IOException {
        Path target = dir.resolve("out.csv");
        Files.writeString(target, "original");
        assertThrows(IOException.class, () -> AtomicFiles.write(target, out -> {
            out.write("partial".getBytes(StandardCharsets.UTF_8));
            throw new IOException("boom");
        }));
        assertEquals("original", Files.readString(target));
        assertEquals(1, fileCount());
    }

    @Test
    void aCancelledWriteLeavesNothing() throws IOException {
        Path target = dir.resolve("new.csv");
        assertThrows(ExportCancelledException.class, () -> AtomicFiles.write(target, out -> {
            throw new ExportCancelledException();
        }));
        assertFalse(Files.exists(target));
        assertEquals(0, fileCount());
    }

    @Test
    void checkWritableAcceptsNewAndExistingFilesWithoutChangingThem() throws IOException {
        Path existing = dir.resolve("a.csv");
        Files.writeString(existing, "keep");
        AtomicFiles.checkWritable(existing);
        AtomicFiles.checkWritable(dir.resolve("brand-new.csv"));
        assertEquals("keep", Files.readString(existing));
        assertEquals(1, fileCount(), "probe files must be removed");
    }

    @Test
    void checkWritableRejectsAMissingFolder() {
        assertThrows(IOException.class, () -> AtomicFiles.checkWritable(dir.resolve("missing").resolve("x.csv")));
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }
}
