package com.jsjjlt.sqlparser.range;

import lombok.Data;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Data
public class TimeRange extends Range {
    private Duration min;
    private Duration max;

    public TimeRange(String min, String max) {
        String[] minSplit = min.split(":");
        String[] maxSplit = max.split(":");
        if (minSplit.length != 3 || maxSplit.length != 3) {
            throw new RuntimeException("Wrong Time pattern: " + min + " + " + max);
        }
        this.min = Duration.ofHours(Integer.parseInt(minSplit[0]))
                .plusMinutes(Integer.parseInt(minSplit[1]))
                .plusSeconds(Integer.parseInt(minSplit[2]));
        this.max = Duration.ofHours(Integer.parseInt(maxSplit[0]))
                .plusMinutes(Integer.parseInt(maxSplit[1]))
                .plusSeconds(Integer.parseInt(maxSplit[2]));
    }

    public Duration getRandomTime(int precision) {
        // 计算 min 和 max 之间的总秒数
        long minSeconds = min.getSeconds();
        long maxSeconds = max.getSeconds();
        long rangeSeconds = maxSeconds - minSeconds;

        // 生成 0 到 rangeSeconds 之间的随机秒数（包含两端）
        long randomSeconds = ThreadLocalRandom.current().nextLong(rangeSeconds + 1);

        // 随机 Duration = min + 随机秒数
        Duration duration = min.plusSeconds(randomSeconds);
        if (precision != 0) {
            duration = duration.plusNanos(ThreadLocalRandom.current().nextInt(999999999));
        }
        return duration;
    }

    @Override
    public <T extends Range> void merge(T range) {
        if (!(range instanceof TimeRange)) {
            throw new RuntimeException("range is not TimeRange");
        }
        TimeRange timeRange = (TimeRange) range;
        min = min.compareTo(timeRange.getMin()) > 0 ? min : timeRange.getMin();
        max = max.compareTo(timeRange.getMax()) > 0 ? timeRange.getMax() : max;
    }
}