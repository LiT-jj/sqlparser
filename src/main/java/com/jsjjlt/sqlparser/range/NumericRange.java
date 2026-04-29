package com.jsjjlt.sqlparser.range;

import lombok.Data;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;

@Data
public class NumericRange extends Range {
    private BigDecimal min;
    private BigDecimal max;

    public NumericRange(int min, int max) {
        this.min = BigDecimal.valueOf(min);
        this.max = BigDecimal.valueOf(max);
    }

    public NumericRange(long min, long max) {
        this.min = BigDecimal.valueOf(min);
        this.max = BigDecimal.valueOf(max);
    }

    public NumericRange(BigInteger min, BigInteger max) {
        this.min = new BigDecimal(min);
        this.max = new BigDecimal(max);
    }

    public NumericRange(BigDecimal min, BigDecimal max) {
        this.min = min;
        this.max = max;
    }

    public NumericRange(String min, String max) {
        this.min = new BigDecimal(min);
        this.max = new BigDecimal(max);
    }

    @Override
    public <T extends Range> void merge(T numericRange) {
        if (!(numericRange instanceof NumericRange)) {
            throw new RuntimeException("range is not NumericRange");
        }
        NumericRange range = (NumericRange) numericRange;
        if (max.compareTo(range.getMin()) < 0 || min.compareTo(range.getMax()) > 0) {
            return;
        }
        min = min.compareTo(range.getMin()) > 0 ? min : range.getMin();
        max = max.compareTo(range.getMax()) > 0 ? range.getMax() : max;
    }

    public BigInteger getRandomBigInteger() {
        BigDecimal randomBigDecimal = getRandomBigDecimal(0);
        BigInteger bigInteger = new BigInteger(randomBigDecimal.toPlainString());
        return bigInteger;
    }

    public BigDecimal getRandomBigDecimal(int scale) {
        if (min.compareTo(max) > 0) {
            throw new RuntimeException("parameter " + min + " should < parameter " + max);
        }
        Random random = new Random();
        double rand = random.nextDouble();
        BigDecimal range = max.subtract(min);
        BigDecimal ans = min.add(range.multiply(new BigDecimal(rand))).setScale(scale, RoundingMode.HALF_UP);
        return ans;
    }

    public static NumericRange build(int precision, int scale, boolean unsigned) {
        StringBuilder parti = new StringBuilder();
        StringBuilder part2 = new StringBuilder();
        for (int i = 0; i < precision - scale; ++i) {
            parti.append('9');
        }
        for (int i = 0; i < scale; ++i) {
            part2.append('9');
        }
        if (unsigned) {
            BigInteger min = new BigInteger("0");
            BigInteger max = new BigInteger(parti + (scale > 0 ? "." + part2 : ""));
            return new NumericRange(min, max);
        } else {
            String nStr = parti + "." + part2;
            BigDecimal min = unsigned ? new BigDecimal("0") : new BigDecimal("-" + nStr);
            BigDecimal max = new BigDecimal(nStr);
            return new NumericRange(min, max);
        }
    }

    public static NumericRange build(int precision, boolean unsigned) {
        return build(precision, 0, unsigned);
    }

    public NumericRange cutOut(int partId, int partNum) {
        BigDecimal part = max.subtract(min).add(new BigDecimal(1)).divide(new BigDecimal(partNum), 2, RoundingMode.HALF_UP);
        BigDecimal newMin = min.add(part.multiply(new BigDecimal(partId - 1)));
        BigDecimal newMax = min.add(part.multiply(new BigDecimal(partId)));
        return new NumericRange(newMin, newMax);
    }

    public static void main(String[] args) {
        NumericRange numericRange = new NumericRange(-9223372036854775808L, 9223372036854775807L);
        System.out.println(numericRange.cutOut(1, 100));
    }
}