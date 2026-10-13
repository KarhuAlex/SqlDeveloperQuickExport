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
    public interface Body<T> {
        T write(OutputStream out) throws IOException;
    }

    private AtomicFiles() {
    }

    /**
     * Fails fast if the target cannot be replaced, e.g. because Excel has it open (Windows)
     * or the directory is read-only. Does not modify an existing file's contents.
     */
    public static void checkWritable(Path target) throws IOException {
        Path file = target.toAbsolutePath();
        Path dir = file.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IOException("Folder does not exist: " + dir);
        }
        if (Files.exists(file)) {
            // WRITE without TRUNCATE_EXISTING takes a write handle but keeps the content;
            // on Windows it fails when another process holds the file open.
            Files.newOutputStream(file, StandardOpenOption.WRITE).close();
        } else {
            Files.delete(Files.createTempFile(dir, ".qexport-probe-", ".tmp"));
        }
    }

    /**
     * Runs {@code body} against a temporary file and, only if it returns normally, moves that
     * file over {@code target}.
     *
     * @return whatever {@code body} returned
     */
    public static <T> T write(Path target, Body<T> body) throws IOException {
        Path file = target.toAbsolutePath();
        Path temp = Files.createTempFile(file.getParent(), ".qexport-", ".tmp");
        try {
            T result;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp), 1 << 16)) {
                result = body.write(out);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return result;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
