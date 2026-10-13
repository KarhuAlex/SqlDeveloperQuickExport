package dev.quickexport.core;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;

/**
 * Adapts an already-executed JDBC {@link ResultSet} to an {@link ExportModel}, reading every
 * column as a plain Java type. Uses only {@code java.sql}; Oracle-specific type codes are
 * handled by their numeric values.
 *
 * <ul>
 *   <li>NUMBER/DECIMAL become {@link BigDecimal}, so no precision is lost.</li>
 *   <li>DATE and TIMESTAMP are read with {@code getTimestamp}, which keeps the time of day
 *       even for Oracle DATE columns.</li>
 *   <li>CLOB/LONG text is cut after {@code lobMaxChars} characters and marked as truncated;
 *       each cut is counted in {@link #getTruncatedLobs()}.</li>
 *   <li>BLOB is exported as a {@code [BLOB n bytes]} placeholder, RAW as {@code [BINARY n bytes]};
 *       the content is never downloaded.</li>
 * </ul>
 *
 * The model is single use: {@link #openRows()} may be called once. The caller owns, and
 * closes, the result set and its statement.
 */
public final class JdbcExportModel implements ExportModel {

    // Oracle-specific type codes (oracle.jdbc.OracleTypes), kept numeric to stay driver-free.
    private static final int ORA_TIMESTAMPTZ = -101;
    private static final int ORA_TIMESTAMPLTZ = -102;
    private static final int ORA_BINARY_FLOAT = 100;
    private static final int ORA_BINARY_DOUBLE = 101;

    private final ResultSet rs;
    private final int lobMaxChars;
    private final String[] names;
    private final int[] types;
    private long truncatedLobs;
    private boolean opened;

    public JdbcExportModel(ResultSet rs, int lobMaxChars) throws SQLException {
        this.rs = rs;
        this.lobMaxChars = lobMaxChars;
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        names = new String[n];
        types = new int[n];
        for (int i = 0; i < n; i++) {
            names[i] = md.getColumnLabel(i + 1);
            types[i] = md.getColumnType(i + 1);
        }
    }

    /**
     * CLOB/LONG values cut to {@code lobMaxChars} so far. The model is read from a single
     * thread, which is also the one that should call this.
     */
    public long getTruncatedLobs() {
        return truncatedLobs;
    }

    @Override
    public int getColumnCount() {
        return names.length;
    }

    @Override
    public String getColumnName(int column) {
        return names[column];
    }

    @Override
    public RowCursor openRows() {
        if (opened) {
            throw new IllegalStateException("A JDBC export model can only be read once");
        }
        opened = true;
        return new RowCursor() {
            @Override
            public boolean next() throws IOException {
                try {
                    return rs.next();
                } catch (SQLException e) {
                    throw new IOException(describe(e), e);
                }
            }

            @Override
            public Object getValue(int column) throws IOException {
                try {
                    return read(column + 1, types[column]);
                } catch (SQLException e) {
                    throw new IOException("Column " + names[column] + ": " + describe(e), e);
                }
            }

            @Override
            public void close() {
                // the caller closes the result set and statement
            }
        };
    }

    private static String describe(SQLException e) {
        return e.getMessage() == null ? e.toString() : e.getMessage().trim();
    }

    private Object read(int c, int type) throws SQLException {
        switch (type) {
            case Types.NUMERIC:
            case Types.DECIMAL:
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
            case Types.FLOAT:
                return rs.getBigDecimal(c);
            case Types.REAL:
            case Types.DOUBLE:
            case ORA_BINARY_FLOAT:
            case ORA_BINARY_DOUBLE: {
                double d = rs.getDouble(c);
                return rs.wasNull() ? null : (Object) d;
            }
            case Types.DATE:
            case Types.TIMESTAMP:
            case ORA_TIMESTAMPLTZ:
                return rs.getTimestamp(c);
            case Types.TIME:
                return rs.getString(c);
            case ORA_TIMESTAMPTZ:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                try {
                    return rs.getObject(c, OffsetDateTime.class);
                } catch (SQLException e) {
                    return rs.getString(c);
                }
            case Types.BOOLEAN:
            case Types.BIT: {
                boolean b = rs.getBoolean(c);
                return rs.wasNull() ? null : (Object) b;
            }
            case Types.CLOB:
            case Types.NCLOB:
                return readClob(c);
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                return readText(c);
            case Types.BLOB: {
                Blob b = rs.getBlob(c);
                return b == null ? null : "[BLOB " + b.length() + " bytes]";
            }
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return rs.getBytes(c);
            default:
                return rs.getString(c);
        }
    }

    private Object readClob(int c) throws SQLException {
        Clob clob = rs.getClob(c);
        if (clob == null) {
            return null;
        }
        long total = clob.length();
        if (total <= lobMaxChars) {
            return clob.getSubString(1, (int) total);
        }
        truncatedLobs++;
        return clob.getSubString(1, lobMaxChars) + marker(total);
    }

    /** LONG columns have no length() and can only be read forwards, once. */
    private Object readText(int c) throws SQLException {
        try (Reader reader = rs.getCharacterStream(c)) {
            if (reader == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            long total = 0;
            int n;
            while ((n = reader.read(buf)) > 0) {
                total += n;
                if (sb.length() < lobMaxChars) {
                    sb.append(buf, 0, Math.min(n, lobMaxChars - sb.length()));
                }
            }
            if (total > lobMaxChars) {
                truncatedLobs++;
                sb.append(marker(total));
            }
            return sb.toString();
        } catch (IOException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    private static String marker(long total) {
        return "\u2026[truncated, " + total + " characters in total]";
    }
}
