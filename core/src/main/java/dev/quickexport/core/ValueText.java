package dev.quickexport.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Date;

/** Text rendering of cell values, shared by both writers. */
final class ValueText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** {@code 2024-01-31 13:45:00} plus a fraction of a second only when there is one. */
    private static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter();

    private ValueText() {
    }

    /** Text for CSV output (and for XLSX cells that cannot be typed). Never null. */
    static String text(Object v, char decimalSeparator) {
        if (v == null) {
            return "";
        }
        if (v instanceof Number n) {
            String s = plainNumber(n);
            return decimalSeparator == '.' ? s : s.replace('.', decimalSeparator);
        }
        if (v instanceof Boolean b) {
            return b.toString();
        }
        if (v instanceof byte[] bytes) {
            return "[BINARY " + bytes.length + " bytes]";
        }
        LocalDate d = asDate(v);
        if (d != null) {
            return DATE.format(d);
        }
        LocalDateTime dt = asDateTime(v);
        if (dt != null) {
            return DATE_TIME.format(dt);
        }
        if (v instanceof OffsetDateTime o) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(o);
        }
        if (v instanceof ZonedDateTime z) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(z.toOffsetDateTime());
        }
        if (v instanceof Instant i) {
            return DateTimeFormatter.ISO_INSTANT.format(i);
        }
        return v.toString();
    }

    /** A date without a time of day, or null if the value is not date-only. */
    static LocalDate asDate(Object v) {
        if (v instanceof LocalDate d) {
            return d;
        }
        // java.sql.Timestamp extends java.util.Date but is a date-time; java.sql.Date is date-only.
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return null;
    }

    /** A wall-clock date-time, or null if the value is not a (zone-less) date-time. */
    static LocalDateTime asDateTime(Object v) {
        if (v instanceof LocalDateTime dt) {
            return dt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.sql.Date) {
            return null;
        }
        if (v instanceof Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

    private static String plainNumber(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return n.toString();
            }
            // Avoid exponent notation such as 1.0E10 for round-trippable plain digits.
            return new BigDecimal(n.toString()).toPlainString();
        }
        return n.toString();
    }
}
