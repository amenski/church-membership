package io.github.membertracker.utils;

/**
 * Utility class for CSV operations.
 */
public final class CsvUtils {

    private CsvUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Escapes a CSV field value by wrapping it in quotes if necessary.
     * Values starting with = + - @ tab or CR get a leading ' so spreadsheets don't run them as formulas (OWASP).
     * 
     * @param value the field value to escape
     * @return the escaped CSV field value
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
