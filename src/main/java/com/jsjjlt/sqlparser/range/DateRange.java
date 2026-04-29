package com.jsjjlt.sqlparser.range;

import lombok.Data;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

@Data
public class DateRange extends Range {
    private LocalDate min;
    private LocalDate max;

    public DateRange(LocalDate min, LocalDate max) {
        this.min = min;
        this.max = max;
    }

    public DateRange(String min, String max) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        this.min = LocalDate.parse(min, formatter);
        this.max = LocalDate.parse(max, formatter);
    }

    public DateRange(String min, String max, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        this.min = LocalDate.parse(min, formatter);
        this.max = LocalDate.parse(max, formatter);
    }

    public LocalDate getRandomDate() {
        long daysBetween = ChronoUnit.DAYS.between(min, max);
        long randomDays = ThreadLocalRandom.current().nextLong(0, daysBetween + 1);
        return min.plusDays(randomDays);
    }

    @Override
    public <T extends Range> void merge(T range) {
        if (!(range instanceof DateRange)) {
            throw new RuntimeException("range is not DateRange");
        }
        DateRange tempRange = (DateRange) range;
        min = min.isAfter(tempRange.getMin()) ? min : tempRange.getMin();
        max = max.isAfter(tempRange.getMax()) ? tempRange.getMax() : max;
    }
}