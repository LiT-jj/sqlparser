package com.jsjjlt.sqlparser.core;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.Join;

import java.util.LinkedHashSet;
import java.util.Set;

public class SelectWalker {
    public Set<Expression> collectJoinOnExpressions(Join join) {
        Set<Expression> onExpressions = new LinkedHashSet<>();
        if (join == null) {
            return onExpressions;
        }
        Expression singleOn = safeGetOnExpression(join);
        if (singleOn != null) {
            onExpressions.add(singleOn);
        }
        if (join.getOnExpressions() != null) {
            onExpressions.addAll(join.getOnExpressions());
        }
        return onExpressions;
    }

    private Expression safeGetOnExpression(Join join) {
        if (join == null || join.isSimple()) {
            return null;
        }
        try {
            return join.getOnExpression();
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
}
