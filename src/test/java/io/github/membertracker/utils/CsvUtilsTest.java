package io.github.membertracker.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvUtilsTest {

    @Test
    void nullBecomesEmpty() {
        assertEquals("", CsvUtils.escapeCsv(null));
    }

    @Test
    void plainValueIsUnchanged() {
        assertEquals("John Doe", CsvUtils.escapeCsv("John Doe"));
    }

    @Test
    void commaIsQuoted() {
        assertEquals("\"Doe, John\"", CsvUtils.escapeCsv("Doe, John"));
    }

    @Test
    void quoteIsDoubledAndQuoted() {
        assertEquals("\"say \"\"hi\"\"\"", CsvUtils.escapeCsv("say \"hi\""));
    }

    @Test
    void newlineIsQuoted() {
        assertEquals("\"a\nb\"", CsvUtils.escapeCsv("a\nb"));
    }

    @Test
    void carriageReturnInsideIsQuoted() {
        assertEquals("\"a\rb\"", CsvUtils.escapeCsv("a\rb"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+39123", "-5", "@SUM(A1)"})
    void formulaPrefixIsNeutralised(String value) {
        assertEquals("'" + value, CsvUtils.escapeCsv(value));
    }

    @Test
    void leadingTabIsPrefixed() {
        assertEquals("'\tx", CsvUtils.escapeCsv("\tx"));
    }

    @Test
    void leadingCarriageReturnIsPrefixedAndQuoted() {
        assertEquals("\"'\rx\"", CsvUtils.escapeCsv("\rx"));
    }

    @Test
    void prefixAndQuotingCombine() {
        assertEquals("\"'=1,2\"", CsvUtils.escapeCsv("=1,2"));
    }

    @Test
    void emptyStringIsUnchanged() {
        assertEquals("", CsvUtils.escapeCsv(""));
    }
}
