package com.cotato.blankit.domain.task.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RepeatMonthDaysConverter implements AttributeConverter<RepeatMonthDays, String> {

    @Override
    public String convertToDatabaseColumn(RepeatMonthDays attribute) {
        return attribute == null ? null : attribute.format();
    }

    @Override
    public RepeatMonthDays convertToEntityAttribute(String dbData) {
        return RepeatMonthDays.parse(dbData);
    }
}
