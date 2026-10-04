package io.github.membertracker.domain.valueobject;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Human-readable month for messages and activity text, e.g. "Oct 2026". Never used in codes or JSON. */
public final class MonthLabel {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private MonthLabel() {
    }

    public static String of(YearMonth month) {
        return FORMAT.format(month);
    }
}
