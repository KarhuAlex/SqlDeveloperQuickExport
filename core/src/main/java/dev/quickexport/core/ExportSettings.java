package dev.quickexport.core;

import java.util.Locale;
import java.util.Properties;

/**
 * User-tunable settings, normally loaded from a small properties file.
 *
 * <pre>
 * csv.delimiter        = ;      (also: comma, semicolon, tab, pipe, or any single character)
 * csv.decimalSeparator = .      (. or ,)
 * csv.bom              = true
 * lob.maxChars         = 32000  (text of a CLOB is exported at most this long)
 * xlsx.querySheet      = true   (put the query on a second worksheet of Excel exports)
 * progress.countRows   = true   (run a row count alongside the export to show a percentage;
 *                                the count runs the query once more on a second session)
 * clipboard.maxChars   = 20000000  (largest result that can be copied to the clipboard)
 * </pre>
 */
public final class ExportSettings {

    public static final char DEFAULT_DELIMITER = ';';
    public static final char DEFAULT_DECIMAL_SEPARATOR = '.';
    public static final int DEFAULT_LOB_MAX_CHARS = 32_000;
    public static final int DEFAULT_CLIPBOARD_MAX_CHARS = 20_000_000;

    private final char delimiter;
    private final char decimalSeparator;
    private final boolean bom;
    private final int lobMaxChars;
    private final boolean querySheet;
    private final boolean countRows;
    private final int clipboardMaxChars;

    /** Creates settings with the query sheet and the row count switched on. */
    public ExportSettings(char delimiter, char decimalSeparator, boolean bom, int lobMaxChars) {
        this(delimiter, decimalSeparator, bom, lobMaxChars, true, true, DEFAULT_CLIPBOARD_MAX_CHARS);
    }

    private ExportSettings(char delimiter, char decimalSeparator, boolean bom, int lobMaxChars,
            boolean querySheet, boolean countRows, int clipboardMaxChars) {
        if (!isValidDelimiter(delimiter)) {
            throw new IllegalArgumentException("Invalid CSV delimiter: " + (int) delimiter);
        }
        if (delimiter == decimalSeparator) {
            throw new IllegalArgumentException("CSV delimiter and decimal separator must differ");
        }
        if (lobMaxChars < 0) {
            throw new IllegalArgumentException("lobMaxChars must be >= 0");
        }
        if (clipboardMaxChars < 0) {
            throw new IllegalArgumentException("clipboardMaxChars must be >= 0");
        }
        this.delimiter = delimiter;
        this.decimalSeparator = decimalSeparator;
        this.bom = bom;
        this.lobMaxChars = lobMaxChars;
        this.querySheet = querySheet;
        this.countRows = countRows;
        this.clipboardMaxChars = clipboardMaxChars;
    }

    public ExportSettings withQuerySheet(boolean value) {
        return new ExportSettings(delimiter, decimalSeparator, bom, lobMaxChars, value, countRows, clipboardMaxChars);
    }

    public ExportSettings withRowCount(boolean value) {
        return new ExportSettings(delimiter, decimalSeparator, bom, lobMaxChars, querySheet, value, clipboardMaxChars);
    }

    public ExportSettings withClipboardMaxChars(int value) {
        return new ExportSettings(delimiter, decimalSeparator, bom, lobMaxChars, querySheet, countRows, value);
    }

    public static ExportSettings defaults() {
        return new ExportSettings(DEFAULT_DELIMITER, DEFAULT_DECIMAL_SEPARATOR, true, DEFAULT_LOB_MAX_CHARS);
    }

    /** Lenient parser: a missing or invalid value falls back to its default. */
    public static ExportSettings fromProperties(Properties p) {
        char delimiter = parseDelimiter(p.getProperty("csv.delimiter"));
        char decimal = parseDecimalSeparator(p.getProperty("csv.decimalSeparator"));
        if (delimiter == decimal) {
            decimal = delimiter == '.' ? ',' : '.';
        }
        boolean bom = !"false".equalsIgnoreCase(trim(p.getProperty("csv.bom")));
        int lobMaxChars = parseNonNegative(p.getProperty("lob.maxChars"), DEFAULT_LOB_MAX_CHARS);
        return new ExportSettings(delimiter, decimal, bom, lobMaxChars)
                .withQuerySheet(!"false".equalsIgnoreCase(trim(p.getProperty("xlsx.querySheet"))))
                .withRowCount(!"false".equalsIgnoreCase(trim(p.getProperty("progress.countRows"))))
                .withClipboardMaxChars(parseNonNegative(p.getProperty("clipboard.maxChars"), DEFAULT_CLIPBOARD_MAX_CHARS));
    }

    public char delimiter() {
        return delimiter;
    }

    public char decimalSeparator() {
        return decimalSeparator;
    }

    public boolean bom() {
        return bom;
    }

    public int lobMaxChars() {
        return lobMaxChars;
    }

    public boolean querySheet() {
        return querySheet;
    }

    public boolean countRows() {
        return countRows;
    }

    public int clipboardMaxChars() {
        return clipboardMaxChars;
    }

    private static boolean isValidDelimiter(char c) {
        return c != '"' && c != '\r' && c != '\n';
    }

    private static char parseDelimiter(String raw) {
        String v = trim(raw);
        if (v == null) {
            return DEFAULT_DELIMITER;
        }
        switch (v.toLowerCase(Locale.ROOT)) {
            case "comma":
                return ',';
            case "semicolon":
                return ';';
            case "tab":
            case "\\t":
                return '\t';
            case "pipe":
                return '|';
            default:
                return v.length() == 1 && isValidDelimiter(v.charAt(0)) ? v.charAt(0) : DEFAULT_DELIMITER;
        }
    }

    private static char parseDecimalSeparator(String raw) {
        String v = trim(raw);
        return v != null && (v.equals(".") || v.equals(",")) ? v.charAt(0) : DEFAULT_DECIMAL_SEPARATOR;
    }

    private static int parseNonNegative(String raw, int fallback) {
        try {
            int n = Integer.parseInt(trim(raw));
            return n >= 0 ? n : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
