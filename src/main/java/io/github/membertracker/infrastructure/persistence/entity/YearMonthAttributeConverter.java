package io.github.membertracker.infrastructure.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/** Stores a YearMonth as YYYY-MM text, the format of the VARCHAR(7) payment.period column. */
@Converter(autoApply = true)
public class YearMonthAttributeConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        return dbData == null ? null : YearMonth.parse(dbData);
    }
}
