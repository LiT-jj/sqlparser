package com.jsjjlt.sqlparser.range;
import lombok.Data;

@Data
public abstract class Range {
    public abstract <T extends Range> void merge(T range);
    public boolean necessary;
}