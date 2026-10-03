package io.github.membertracker.infrastructure.persistence;

import io.github.membertracker.infrastructure.persistence.entity.YearMonthAttributeConverter;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class YearMonthAttributeConverterTest {

    private final YearMonthAttributeConverter converter = new YearMonthAttributeConverter();

    @Test
    void convertsToYyyyMmText() {
        assertThat(converter.convertToDatabaseColumn(YearMonth.of(2026, 10))).isEqualTo("2026-10");
    }

    @Test
    void parsesSeedDataText() {
        assertThat(converter.convertToEntityAttribute("2023-01")).isEqualTo(YearMonth.of(2023, 1));
    }

    @Test
    void roundTrips() {
        YearMonth month = YearMonth.of(2024, 2);
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(month))).isEqualTo(month);
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
