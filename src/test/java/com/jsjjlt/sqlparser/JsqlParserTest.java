package com.jsjjlt.sqlparser;

import com.jsjjlt.sqlparser.constraint.ColumnConstraint;
import com.jsjjlt.sqlparser.entity.Equal;
import com.jsjjlt.sqlparser.entity.RefCol;
import com.jsjjlt.sqlparser.entity.Relate;
import com.jsjjlt.sqlparser.entity.SQLContext;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class JsqlParserTest {

    private final JsqlParser parser = new JsqlParser();

    @Test
    void whereShouldExtractTableColumnAndEqualsConstraint() {
        SQLContext ctx = parser.parse("select * from t1 where a = 1").getContext();
        assertSetEquals(setOf("t1"), toTableSet(ctx));
        assertSetEquals(setOf("t1.*", "t1.a"), toColumnSet(ctx));
        assertConstraintEquals(ctx, "t1.a", setOf("1"), Collections.<String>emptySet());
    }

    @Test
    void joinShouldExtractRelateConstraint() {
        SQLContext ctx = parser.parse("select * from db1.tab1 t1 left join db2.tab2 t2 on t1.a = t2.b where t1.c = 4").getContext();
        assertSetEquals(setOf("db1.tab1", "db2.tab2"), toTableSet(ctx));
        assertSetEquals(setOf("db1.tab1.a", "db2.tab2.b", "db1.tab1.c", "*"), toColumnSet(ctx));
        assertConstraintEquals(ctx, "db1.tab1.c", setOf("4"), Collections.<String>emptySet());
        assertConstraintEquals(ctx, "db1.tab1.a", Collections.<String>emptySet(), setOf("= db2.tab2.b"));
    }

    @Test
    void inShouldExtractEqualsValues() {
        SQLContext ctx = parser.parse("select * from t1 where a in (1,2,3)").getContext();
        assertSetEquals(setOf("t1"), toTableSet(ctx));
        assertSetEquals(setOf("t1.*", "t1.a"), toColumnSet(ctx));
        assertConstraintEquals(ctx, "t1.a", setOf("1", "2", "3"), Collections.<String>emptySet());
    }

    @Test
    void unionAliasConstantShouldExpandToAllSources() {
        SQLContext ctx = parser.parse("select * from (select c from t1 union select c from t2) as tab1 where tab1.c = 4").getContext();
        assertSetEquals(setOf("tab1", "t1", "t2"), toTableSet(ctx));
        assertSetEquals(setOf("*", "t1.c", "t2.c"), toColumnSet(ctx));
        assertConstraintEquals(ctx, "t1.c", setOf("4"), Collections.<String>emptySet());
        assertConstraintEquals(ctx, "t2.c", setOf("4"), Collections.<String>emptySet());
    }

    @Test
    void subqueryJoinShouldExpandBothSides() {
        SQLContext ctx = parser.parse("select * from (select c from t1 union select c from t2 union select c from t3) as tab1 left join (select c from t4 union select c from t5) as tab2 on tab1.c = tab2.c").getContext();
        assertSetEquals(setOf("t1", "t2", "t3", "t4", "t5", "tab1", "tab2"), toTableSet(ctx));
        assertSetEquals(setOf("*", "t1.c", "t2.c", "t3.c", "t4.c", "t5.c"), toColumnSet(ctx));
        assertConstraintEquals(ctx, "t1.c", Collections.<String>emptySet(), setOf("= t4.c", "= t5.c"));
        assertConstraintEquals(ctx, "t2.c", Collections.<String>emptySet(), setOf("= t4.c", "= t5.c"));
        assertConstraintEquals(ctx, "t3.c", Collections.<String>emptySet(), setOf("= t4.c", "= t5.c"));
    }

    @Test
    void strictModeShouldThrowOnInvalidSql() {
        assertThrows(ParseException.class, () -> parser.parse("select * from t=4", true));
    }

    private static void assertConstraintEquals(SQLContext ctx, String column, Set<String> expectEquals, Set<String> expectRelates) {
        Map<String, ColumnConstraint> map = ctx.getColumn2constraint().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
        assertTrue(map.containsKey(column), "missing constraint key: " + column);
        ColumnConstraint cc = map.get(column);
        Set<String> equals = cc.getEquals() == null ? Collections.<String>emptySet() :
                cc.getEquals().stream().map(Equal::getValue).collect(Collectors.toSet());
        Set<String> relates = cc.getRelates() == null ? Collections.<String>emptySet() :
                cc.getRelates().stream().map(JsqlParserTest::toRelateText).collect(Collectors.toSet());
        assertSetEquals(expectEquals, equals);
        assertSetEquals(expectRelates, relates);
    }

    private static String toRelateText(Relate r) {
        RefCol c = r.getColumn();
        return r.getOperator() + " " + (c == null ? "null" : c.toString());
    }

    private static Set<String> toTableSet(SQLContext ctx) {
        return ctx.getTables().stream().map(Object::toString).collect(Collectors.toSet());
    }

    private static Set<String> toColumnSet(SQLContext ctx) {
        return ctx.getColumns().stream().map(Object::toString).collect(Collectors.toSet());
    }

    private static Set<String> setOf(String... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    private static void assertSetEquals(Set<String> expected, Set<String> actual) {
        assertEquals(expected, actual);
    }
}
