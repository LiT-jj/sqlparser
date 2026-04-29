package com.jsjjlt.sqlparser.core;

import net.sf.jsqlparser.schema.Column;

import java.util.Locale;
import java.util.function.Function;

public class ColumnResolver {
    private final Function<String, String> sanitizeIdentifier;

    public ColumnResolver(Function<String, String> sanitizeIdentifier) {
        this.sanitizeIdentifier = sanitizeIdentifier;
    }

    public String normalizeColumnKey(Column column) {
        if (column == null) {
            return "";
        }
        String name = sanitizeIdentifier.apply(column.getColumnName());
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
