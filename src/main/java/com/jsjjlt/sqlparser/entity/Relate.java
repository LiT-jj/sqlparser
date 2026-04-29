package com.jsjjlt.sqlparser.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Relate {
    String operator;
    RefCol column;
    boolean necessary;

    public Relate(String operator, RefCol column) {
        this.operator = operator;
        this.column = column;
    }
}