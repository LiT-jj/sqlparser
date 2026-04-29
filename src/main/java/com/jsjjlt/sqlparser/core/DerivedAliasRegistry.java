package com.jsjjlt.sqlparser.core;

import com.jsjjlt.sqlparser.entity.RefCol;
import com.jsjjlt.sqlparser.entity.RefTab;

import java.util.*;

public class DerivedAliasRegistry {
    private final Map<String, Map<String, Set<RefCol>>> derivedAliasColumnMap = new HashMap<>();
    private final Map<String, Boolean> derivedAliasFromSetOp = new HashMap<>();
    private final Map<String, Set<RefTab>> derivedAliasSourceTables = new HashMap<>();
    private final Deque<Map<String, RefTab>> aliasScopeStack = new ArrayDeque<>();

    public Map<String, Map<String, Set<RefCol>>> columnMap() {
        return derivedAliasColumnMap;
    }

    public Map<String, Boolean> fromSetOpMap() {
        return derivedAliasFromSetOp;
    }

    public Map<String, Set<RefTab>> sourceTablesMap() {
        return derivedAliasSourceTables;
    }

    public Deque<Map<String, RefTab>> aliasScopeStack() {
        return aliasScopeStack;
    }
}
