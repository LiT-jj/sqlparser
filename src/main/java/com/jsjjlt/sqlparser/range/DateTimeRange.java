package com.jsjjlt.sqlparser.range;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Data
public class DateTimeRange extends Range {
    private LocalDateTime min;
    private LocalDateTime max;

    public DateTimeRange(LocalDateTime min, LocalDateTime max) {
        this.min = min;
        this.max = max;
    }

    public DateTimeRange(String min, String max) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.min = LocalDateTime.parse(min, formatter);
        this.max = LocalDateTime.parse(max, formatter);
    }

    public LocalDateTime getRandomDateTime(int precision) {
        long startEpoch = min.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = max.atZone(ZoneId.systemDefault()).toEpochSecond();
        long randomEpoch = ThreadLocalRandom.current().nextLong(startEpoch, endEpoch);
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(randomEpoch, 0, ZoneId.systemDefault().getRules().getOffset(min));
        if (precision != 0) {
            localDateTime = localDateTime.withNano(ThreadLocalRandom.current().nextInt(0, 999999999));
        }
        return localDateTime;
    }

    @Override
    public <T extends Range> void merge(T range) {
        if (!(range instanceof DateTimeRange)) {
            throw new RuntimeException("range is not DateTimeRange");
        }
        DateTimeRange dateTimeRange = (DateTimeRange) range;
        min = min.isAfter(dateTimeRange.getMin()) ? min : dateTimeRange.getMin();
        max = max.isAfter(dateTimeRange.getMax()) ? dateTimeRange.getMax() : max;
    }
}