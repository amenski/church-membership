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
     * 
     * @param value the field value to escape
     * @return the escaped CSV field value
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
