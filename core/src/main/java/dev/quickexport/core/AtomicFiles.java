package dev.quickexport.core;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes an export to a temporary file next to the target and moves it into place when it is
 * complete, so a failed or cancelled export never leaves a truncated file or destroys an
 * existing one.
 */
public final class AtomicFiles {

    @FunctionalInterface
    public interface Body {
        void write(OutputStream out) throws IOException;
    }

    private AtomicFiles() {
    }

    /**
     * Fails fast if the target cannot be replaced, e.g. because Excel has it open (Windows)
     * or the directory is read-only. Does not modify an existing file's contents.
     */
    public static void checkWritable(Path target) throws IOException {
        Path abs = target.toAbsolutePath();
        Path dir = abs.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IOException("Folder does not exist: " + dir);
        }
        if (Files.exists(abs)) {
            // Opening with WRITE only (no TRUNCATE_EXISTING) takes a write handle without
            // changing the content; this fails on Windows when another process holds the file.
            try (OutputStream ignored = Files.newOutputStream(abs, StandardOpenOption.WRITE)) {
                // nothing to write
            }
        } else {
            Path probe = Files.createTempFile(dir, ".qexport-probe-", ".tmp");
            Files.delete(probe);
        }
    }

    public static void write(Path target, Body body) throws IOException {
        Path abs = target.toAbsolutePath();
        Path tmp = Files.createTempFile(abs.getParent(), ".qexport-", ".tmp");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16)) {
                body.write(out);
            }
            try {
                Files.move(tmp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
