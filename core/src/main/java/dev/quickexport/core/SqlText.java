package dev.quickexport.core;

/** Small helpers for SQL text taken from a worksheet. */
public final class SqlText {

    private SqlText() {
    }

    /**
     * Wraps a query in a row count. The query and the closing parenthesis sit on their own lines,
     * so a trailing {@code --} comment in the query cannot swallow the parenthesis.
     */
    public static String countQuery(String sql) {
        return "select count(*) from (\n" + stripTerminator(sql) + "\n)";
    }

    /**
     * Removes statement terminators that a worksheet accepts but JDBC rejects: trailing
     * semicolons and a lone {@code /} on the last line, in any combination.
     */
    public static String stripTerminator(String sql) {
        String s = sql.strip();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (s.endsWith(";")) {
                s = s.substring(0, s.length() - 1).stripTrailing();
                changed = true;
            }
            int nl = Math.max(s.lastIndexOf('\n'), s.lastIndexOf('\r'));
            if (nl >= 0 && s.substring(nl + 1).strip().equals("/")) {
                s = s.substring(0, nl).stripTrailing();
                changed = true;
            }
        }
        return s;
    }
}
