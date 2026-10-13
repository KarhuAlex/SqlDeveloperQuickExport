package dev.quickexport.core;

import java.util.Properties;

/**
 * User-tunable settings, normally loaded from a small properties file.
 *
 * <pre>
 * csv.delimiter        = ;      (also: comma, semicolon, tab, pipe, or any single character)
 * csv.decimalSeparator = .      (. or ,)
 * csv.bom              = true
 * lob.maxChars         = 32000  (text of a CLOB exported at most this long)
 * </pre>
 */
public final class ExportSettings {

    public static final char DEFAULT_DELIMITER = ';';
    public static final char DEFAULT_DECIMAL_SEPARATOR = '.';
    public static final int DEFAULT_LOB_MAX_CHARS = 32_000;

    private final char delimiter;
    private final char decimalSeparator;
    private final boolean bom;
    private final int lobMaxChars;

    public ExportSettings(char delimiter, char decimalSeparator, boolean bom, int lobMaxChars) {
        if (delimiter == '"' || delimiter == '\r' || delimiter == '\n') {
            throw new IllegalArgumentException("Invalid CSV delimiter: " + (int) delimiter);
        }
        if (delimiter == decimalSeparator) {
            throw new IllegalArgumentException("CSV delimiter and decimal separator must differ");
        }
        if (lobMaxChars < 0) {
            throw new IllegalArgumentException("lobMaxChars must be >= 0");
        }
        this.delimiter = delimiter;
        this.decimalSeparator = decimalSeparator;
        this.bom = bom;
        this.lobMaxChars = lobMaxChars;
    }

    public static ExportSettings defaults() {
        return new ExportSettings(DEFAULT_DELIMITER, DEFAULT_DECIMAL_SEPARATOR, true, DEFAULT_LOB_MAX_CHARS);
    }

    /** Lenient parser: any missing or invalid value falls back to its default. */
    public static ExportSettings fromProperties(Properties p) {
        char delimiter = parseDelimiter(p.getProperty("csv.delimiter"));
        char decimal = parseDecimal(p.getProperty("csv.decimalSeparator"));
        if (delimiter == decimal) {
            // e.g. delimiter "," with decimal ","; keep the explicitly chosen delimiter valid
            decimal = delimiter == '.' ? ',' : '.';
        }
        boolean bom = !"false".equalsIgnoreCase(trim(p.getProperty("csv.bom")));
        int lob = parseInt(p.getProperty("lob.maxChars"), DEFAULT_LOB_MAX_CHARS);
        try {
            return new ExportSettings(delimiter, decimal, bom, lob);
        } catch (IllegalArgumentException e) {
            return defaults();
        }
    }

    private static char parseDelimiter(String raw) {
        String v = trim(raw);
        if (v == null || v.isEmpty()) {
            return DEFAULT_DELIMITER;
        }
        switch (v.toLowerCase()) {
            case "comma": return ',';
            case "semicolon": return ';';
            case "tab": case "\\t": return '\t';
            case "pipe": return '|';
            default:
                boolean usable = v.length() == 1 && v.charAt(0) != '"' && v.charAt(0) != '\r' && v.charAt(0) != '\n';
                return usable ? v.charAt(0) : DEFAULT_DELIMITER;
        }
    }

    private static char parseDecimal(String raw) {
        String v = trim(raw);
        if (v != null && v.length() == 1 && (v.charAt(0) == '.' || v.charAt(0) == ',')) {
            return v.charAt(0);
        }
        return DEFAULT_DECIMAL_SEPARATOR;
    }

    private static int parseInt(String raw, int fallback) {
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
}
