package dev.quickexport.core;

import java.io.IOException;
import java.io.Writer;
import java.text.NumberFormat;

/**
 * Collects text in memory and refuses to grow past a limit, so copying a huge result to the
 * clipboard fails with a clear message instead of exhausting the heap.
 */
public final class LimitedWriter extends Writer {

    private final StringBuilder text = new StringBuilder();
    private final long maxChars;

    public LimitedWriter(long maxChars) {
        this.maxChars = maxChars;
    }

    @Override
    public void write(char[] buffer, int offset, int length) throws IOException {
        ensureRoom(length);
        text.append(buffer, offset, length);
    }

    @Override
    public void write(String s, int offset, int length) throws IOException {
        ensureRoom(length);
        text.append(s, offset, offset + length);
    }

    @Override
    public void flush() {
        // nothing is buffered beyond the string itself
    }

    @Override
    public void close() {
        // nothing to release
    }

    @Override
    public String toString() {
        return text.toString();
    }

    private void ensureRoom(int length) throws IOException {
        if (text.length() + (long) length > maxChars) {
            throw new IOException("The result is larger than the clipboard limit of "
                    + NumberFormat.getIntegerInstance().format(maxChars)
                    + " characters. Use Quick Export to CSV to save it as a file instead.");
        }
    }
}
