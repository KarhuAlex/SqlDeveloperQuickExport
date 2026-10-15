package dev.quickexport.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class ExportSettingsTest {

    private static ExportSettings parse(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return ExportSettings.fromProperties(p);
    }

    @Test
    void defaultsAreSemicolonDotAndBom() {
        ExportSettings s = parse();
        assertEquals(';', s.delimiter());
        assertEquals('.', s.decimalSeparator());
        assertTrue(s.bom());
        assertEquals(32_000, s.lobMaxChars());
    }

    @Test
    void delimiterAcceptsNamesAndLiterals() {
        assertEquals(',', parse("csv.delimiter", "comma").delimiter());
        assertEquals(',', parse("csv.delimiter", "COMMA").delimiter());
        assertEquals('\t', parse("csv.delimiter", "tab").delimiter());
        assertEquals('|', parse("csv.delimiter", " | ").delimiter());
        assertEquals(';', parse("csv.delimiter", "semicolon").delimiter());
    }

    @Test
    void invalidValuesFallBackToDefaultsIndependently() {
        ExportSettings s = parse("csv.delimiter", "toolong", "csv.decimalSeparator", "x", "lob.maxChars", "-5");
        assertEquals(';', s.delimiter());
        assertEquals('.', s.decimalSeparator());
        assertEquals(32_000, s.lobMaxChars());

        ExportSettings quote = parse("csv.delimiter", "\"", "csv.decimalSeparator", ",", "lob.maxChars", "10");
        assertEquals(';', quote.delimiter());
        assertEquals(',', quote.decimalSeparator());
        assertEquals(10, quote.lobMaxChars());
    }

    @Test
    void delimiterAndDecimalSeparatorNeverCollide() {
        ExportSettings s = parse("csv.delimiter", ",", "csv.decimalSeparator", ",");
        assertEquals(',', s.delimiter());
        assertEquals('.', s.decimalSeparator());
    }

    @Test
    void bomCanBeDisabled() {
        assertFalse(parse("csv.bom", "false").bom());
        assertTrue(parse("csv.bom", "anything else").bom());
    }

    @Test
    void clipboardLimitHasADefaultAndAcceptsOnlyNonNegativeNumbers() {
        assertEquals(20_000_000, parse().clipboardMaxChars());
        assertEquals(5_000, parse("clipboard.maxChars", "5000").clipboardMaxChars());
        assertEquals(20_000_000, parse("clipboard.maxChars", "-1").clipboardMaxChars());
        assertEquals(20_000_000, parse("clipboard.maxChars", "lots").clipboardMaxChars());
        assertEquals(5_000, parse().withClipboardMaxChars(5_000).clipboardMaxChars());
        assertTrue(parse().withClipboardMaxChars(5_000).countRows(), "changing one setting keeps the others");
    }

    @Test
    void rowCountIsOnByDefaultAndCanBeDisabled() {
        assertTrue(parse().countRows());
        assertFalse(parse("progress.countRows", "false").countRows());
        assertTrue(parse("progress.countRows", "maybe").countRows());
        assertFalse(parse().withRowCount(false).countRows());
        assertTrue(parse().withRowCount(false).querySheet(), "changing one switch keeps the others");
    }

    @Test
    void querySheetIsOnByDefaultAndCanBeDisabled() {
        assertTrue(parse().querySheet());
        assertFalse(parse("xlsx.querySheet", "false").querySheet());
        assertTrue(parse("xlsx.querySheet", "yes please").querySheet());
    }

    @Test
    void constructorRejectsInvalidCombinations() {
        assertThrows(IllegalArgumentException.class, () -> new ExportSettings('"', '.', true, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExportSettings('\n', '.', true, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExportSettings(',', ',', true, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExportSettings(';', '.', true, -1));
    }
}
