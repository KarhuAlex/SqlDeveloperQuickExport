package dev.quickexport.core;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.sql.SQLException;
import java.util.Locale;

/** Turns an export failure into a message that says what the user can do about it. */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static String describe(Throwable error) {
        Throwable cause = mostSpecific(error);

        if (cause instanceof AccessDeniedException) {
            return "You do not have permission to write to:\n" + ((FileSystemException) cause).getFile();
        }
        if (cause instanceof FileSystemException) {
            FileSystemException e = (FileSystemException) cause;
            if (isLockedByAnotherProcess(e)) {
                return "The file is open in another program, for example Excel.\n"
                        + "Close it and export again:\n" + e.getFile();
            }
            return "Could not write the file:\n" + e.getFile() + (e.getReason() == null ? "" : "\n" + e.getReason());
        }
        if (cause instanceof SQLException) {
            return "The query failed:\n" + trimmed(cause.getMessage());
        }
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (error instanceof IOException) {
            return "The export failed:\n" + message;
        }
        return "Unexpected error:\n" + error.getClass().getSimpleName() + ": " + message;
    }

    /** The first file-system or SQL exception in the cause chain, otherwise the error itself. */
    private static Throwable mostSpecific(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException || t instanceof FileSystemException) {
                return t;
            }
        }
        return error;
    }

    private static boolean isLockedByAnotherProcess(FileSystemException e) {
        String reason = e.getReason() == null ? "" : e.getReason().toLowerCase(Locale.ROOT);
        return reason.contains("another process") || reason.contains("being used");
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }
}
