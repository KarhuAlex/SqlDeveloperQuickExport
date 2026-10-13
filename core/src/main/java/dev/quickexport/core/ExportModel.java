package dev.quickexport.core;

import java.io.Closeable;
import java.io.IOException;

/**
 * Read-only, forward-only view of a result set. Implementations adapt whatever the data
 * comes from (a grid model, a JDBC ResultSet, a test fixture). Values must already be plain
 * Java objects: LOBs converted to {@code String} (or {@code byte[]} for binary), vendor
 * number/date types converted to {@link Number}, {@code java.time} or {@code java.sql} types.
 */
public interface ExportModel {

    int getColumnCount();

    String getColumnName(int column);

    /** Opens a fresh cursor positioned before the first row. The caller closes it. */
    RowCursor openRows() throws IOException;

    interface RowCursor extends Closeable {

        /** Advances to the next row; returns false when there are no more rows. */
        boolean next() throws IOException;

        /** Value of the given 0-based column in the current row; {@code null} for SQL NULL. */
        Object getValue(int column) throws IOException;
    }
}
