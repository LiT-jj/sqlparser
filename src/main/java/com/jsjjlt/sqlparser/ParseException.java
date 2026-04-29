package com.jsjjlt.sqlparser;

import java.util.List;

public class ParseException extends RuntimeException {
    private final List<ParseError> errors;

    public ParseException(String message, List<ParseError> errors) {
        super(message);
        this.errors = errors;
    }

    public List<ParseError> getErrors() {
        return errors;
    }
}
