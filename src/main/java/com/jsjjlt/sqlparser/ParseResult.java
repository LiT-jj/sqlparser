package com.jsjjlt.sqlparser;

import com.jsjjlt.sqlparser.entity.SQLContext;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class ParseResult {
    private SQLContext context;
    private List<ParseError> errors;

    public ParseResult() {
        this.context = new SQLContext();
        this.errors = new ArrayList<>();
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
