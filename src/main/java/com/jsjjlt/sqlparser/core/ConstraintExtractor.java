package com.jsjjlt.sqlparser.core;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.*;

public class ConstraintExtractor {
    public boolean isBinaryComparison(Expression expression) {
        return expression instanceof EqualsTo
                || expression instanceof NotEqualsTo
                || expression instanceof GreaterThan
                || expression instanceof GreaterThanEquals
                || expression instanceof MinorThan
                || expression instanceof MinorThanEquals;
    }
}
