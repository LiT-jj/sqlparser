package com.jsjjlt.sqlparser;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParseError {
    private String stage;
    private String sql;
    private String statement;
    private String message;
    private Throwable cause;
}
