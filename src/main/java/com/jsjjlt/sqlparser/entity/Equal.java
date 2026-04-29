package com.jsjjlt.sqlparser.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Equal {
    public String value;
    public Boolean necessary;

    public Equal(String value) {
        this.value = value;
    }
}