package io.github.membertracker.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Utility class for CSV operations.
 */
public final class CsvUtils {

    private static final Pattern PHONE_OR_NUMBER = Pattern.compile("^[+\\-]?[0-9 ().\\-]+$");

    private CsvUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Escapes a CSV field value by wrapping it in quotes if necessary.
     * Values starting with = + - @ tab or CR get a leading ' so spreadsheets don't run them as formulas (OWASP).
     * Exception: a value made only of digits, spaces and the characters + - ( ) . (a phone number or a plain
     * number such as "+39 333 1234567" or "-12.50") has no letters or '=', so it cannot call a spreadsheet
     * function and is left unprefixed. This also leaves harmless arithmetic like "-2+3" alone.
     * 
     * @param value the field value to escape
     * @return the escaped CSV field value
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0
                && !PHONE_OR_NUMBER.matcher(safe).matches()) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /**
     * Wraps CSV text in a plain download response: UTF-8 with a byte order mark, so Excel reads non-Latin
     * names (for example Amharic) correctly.
     */
    public static ResponseEntity<byte[]> attachment(String filename, String csv) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }
}
