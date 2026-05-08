package com.jsjjlt.sqlparser;

import com.jsjjlt.sqlparser.entity.*;
import com.jsjjlt.sqlparser.constraint.ColumnConstraint;
import com.jsjjlt.sqlparser.core.ColumnResolver;
import com.jsjjlt.sqlparser.core.ConstraintExtractor;
import com.jsjjlt.sqlparser.core.DerivedAliasRegistry;
import com.jsjjlt.sqlparser.core.SelectWalker;
import com.jsjjlt.sqlparser.range.StringRange;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;

import java.util.*;

public class JsqlParser {
    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(JsqlParser.class);
    private static class ParseSession {
        final DerivedAliasRegistry derivedAliasRegistry;
        final SelectWalker selectWalker;
        final ColumnResolver columnResolver;
        final ConstraintExtractor constraintExtractor;

        ParseSession() {
            this.derivedAliasRegistry = new DerivedAliasRegistry();
            this.selectWalker = new SelectWalker();
            this.columnResolver = new ColumnResolver(this::sanitizeIdentifier);
            this.constraintExtractor = new ConstraintExtractor();
        }

        private String sanitizeIdentifier(String name) {
            if (name == null) {
                return null;
            }
            String ans = name.trim();
            if (ans.length() >= 2) {
                char first = ans.charAt(0);
                char last = ans.charAt(ans.length() - 1);
                if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    ans = ans.substring(1, ans.length() - 1);
                }
            }
            return ans;
        }
    }

    public SQLContext getSQLContext(String sql) {
        return parse(sql, false).getContext();
    }

    public SQLContext getSQLContext(String sql, boolean strict) {
        return parse(sql, strict).getContext();
    }

    public ParseResult parse(String sql) {
        return parse(sql, false);
    }

    public ParseResult parse(String sql, boolean strict) {
        ParseSession session = new ParseSession();
        ParseResult result = new ParseResult();
        SQLContext finalContext = result.getContext();
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            for (Statement statement : statements) {
                try {
                    SQLContext context = convertStatement(statement, session);
                    if (context != null) {
                        finalContext.merge(context);
                    }
                } catch (IndexOutOfBoundsException e) {
                    result.getErrors().add(new ParseError(
                            "convert-statement-index",
                            sql,
                            String.valueOf(statement),
                            e.getMessage(),
                            e
                    ));
                    logger.debug("Skip statement due to index issue, statement={}", statement, e);
                } catch (Exception e) {
                    result.getErrors().add(new ParseError(
                            "convert-statement",
                            sql,
                            String.valueOf(statement),
                            e.getMessage(),
                            e
                    ));
                    logger.debug("Skip statement due to parse/convert error, statement={}", statement, e);
                }
            }
        } catch (Exception e) {
            result.getErrors().add(new ParseError(
                    "parse-sql",
                    sql,
                    null,
                    e.getMessage(),
                    e
            ));
            logger.debug("Failed to parse sql into statements, sql={}", sql, e);
        }
        if (strict && result.hasErrors()) {
            throw new ParseException("SQL parse failed in strict mode.", result.getErrors());
        }
        return result;
    }

    private SQLContext convertStatement(Statement statement, ParseSession session) {
        SQLContext context = new SQLContext();
        if (statement instanceof Select) {
            context.setQueryType(SQLContext.QUERY);
            convertSelectBody((Select) statement, context, session);
        } else if (statement instanceof Update) {
            context.setQueryType(SQLContext.UPDATE);
            Update update = (Update) statement;
            if (update.getTable() != null) {
                context.addTableToRefTab(update.getTable());
            }
            if (update.getWhere() != null) {
                extractConstraints(update.getWhere(), context, session);
            }
        } else if (statement instanceof Insert) {
            context.setQueryType(SQLContext.INSERT);
            Insert insert = (Insert) statement;
            context.addTableToRefTab(insert.getTable());
            if (insert.getColumns() != null) {
                RefTab table = toRefTab(insert.getTable());
                for (Column column : insert.getColumns()) {
                    context.addColumn(new RefCol(table, column.getColumnName(), null));
                }
            }
        } else if (statement instanceof Delete) {
            context.setQueryType(SQLContext.DELETE);
            Delete delete = (Delete) statement;
            context.addTableToRefTab(delete.getTable());
            if (delete.getWhere() != null) {
                extractConstraints(delete.getWhere(), context, session);
            }
        }
        return context;
    }

    private void convertSelectBody(Select selectBody, SQLContext context, ParseSession session) {
        if (selectBody instanceof PlainSelect) {
            convertPlainSelect((PlainSelect) selectBody, context, session);
            return;
        }
        if (selectBody instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) selectBody;
            if (setOperationList.getSelects() != null) {
                for (Select body : setOperationList.getSelects()) {
                    convertSelectBody(body, context, session);
                }
            }
            return;
        }
        if (selectBody instanceof ParenthesedSelect) {
            convertSelectBody(((ParenthesedSelect) selectBody).getSelect(), context, session);
        }
    }

    private void convertPlainSelect(PlainSelect plainSelect, SQLContext context, ParseSession session) {
        session.derivedAliasRegistry.aliasScopeStack().push(buildLocalAliasTableMap(plainSelect));
        try {
            if (plainSelect.getFromItem() != null) {
                convertFromItem(plainSelect.getFromItem(), context, session);
            }
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    convertFromItem(join.getRightItem(), context, session);
                    Set<Expression> onExpressions = session.selectWalker.collectJoinOnExpressions(join);
                    for (Expression onExpression : onExpressions) {
                        extractConstraints(onExpression, context, session);
                    }
                }
            }
            if (plainSelect.getSelectItems() != null) {
                for (SelectItem<?> selectItem : plainSelect.getSelectItems()) {
                    convertSelectItem(selectItem, context, session);
                }
            }
            if (plainSelect.getWhere() != null) {
                extractConstraints(plainSelect.getWhere(), context, session);
            }
            if (plainSelect.getHaving() != null) {
                extractConstraints(plainSelect.getHaving(), context, session);
            }
        } finally {
            session.derivedAliasRegistry.aliasScopeStack().pop();
        }
    }

    private void convertFromItem(FromItem fromItem, SQLContext context, ParseSession session) {
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            RefTab refTab = toRefTab(table);
            if (table.getAlias() != null) {
                refTab.addAlias(table.getAlias().getName());
            }
            context.addTable(refTab);
            return;
        }
        if (fromItem instanceof ParenthesedFromItem) {
            ParenthesedFromItem parenthesedFromItem = (ParenthesedFromItem) fromItem;
            // 兼容 ((select ...) alias) 场景：将最外层 alias 也注册为派生来源
            if (parenthesedFromItem.getAlias() != null
                    && parenthesedFromItem.getAlias().getName() != null
                    && parenthesedFromItem.getFromItem() instanceof ParenthesedSelect) {
                ParenthesedSelect innerSelect = (ParenthesedSelect) parenthesedFromItem.getFromItem();
                String aliasName = parenthesedFromItem.getAlias().getName();
                registerDerivedAliasColumns(aliasName, innerSelect.getSelect(), session);
                context.addTable(new RefTab(aliasName));
                return;
            }
        }
        if (fromItem instanceof ParenthesedSelect) {
            ParenthesedSelect parenthesedSelect = (ParenthesedSelect) fromItem;
            // 子查询别名需要作为“可知的表”加入上下文，例如: (...) temp
            if (parenthesedSelect.getAlias() != null && parenthesedSelect.getAlias().getName() != null) {
                String aliasName = parenthesedSelect.getAlias().getName();
                registerDerivedAliasColumns(aliasName, parenthesedSelect.getSelect(), session);
                RefTab aliasTable = new RefTab(aliasName);
                context.addTable(aliasTable);
            }
            // 子查询单独解析，避免子查询中的表别名污染外层作用域（例如内层 b/f 覆盖外层 b/f）
            mergeSubqueryContext(parenthesedSelect.getSelect(), context, true, session);
        }
    }

    private void registerDerivedAliasColumns(String aliasName, Select select, ParseSession session) {
        if (aliasName == null || select == null) {
            return;
        }
        String aliasKey = aliasName.toLowerCase(Locale.ROOT);
        session.derivedAliasRegistry.fromSetOpMap().put(aliasKey, isSetOperationSelect(select));
        Set<RefTab> sourceTables = new LinkedHashSet<>();
        collectSourceTables(select, sourceTables);
        if (!sourceTables.isEmpty()) {
            session.derivedAliasRegistry.sourceTablesMap().put(aliasKey, sourceTables);
        }
        Map<String, Set<RefCol>> colMap = new LinkedHashMap<>();
        collectDerivedColumns(select, colMap, session);
        // 对派生列做递归回填，尽量把 c/x/tmp 这类别名还原到真实来源列集合
        Map<String, Set<RefCol>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Set<RefCol>> entry : colMap.entrySet()) {
            Set<RefCol> resolvedSet = new LinkedHashSet<>();
            for (RefCol candidate : entry.getValue()) {
                resolvedSet.addAll(resolveDerivedRefColCandidates(candidate, new HashSet<>(), session));
            }
            if (!resolvedSet.isEmpty()) {
                normalized.put(entry.getKey(), resolvedSet);
            }
        }
        if (!colMap.isEmpty()) {
            session.derivedAliasRegistry.columnMap().put(aliasKey, normalized);
        }
    }

    private void collectSourceTables(Select select, Set<RefTab> tables) {
        if (select == null || tables == null) {
            return;
        }
        if (select instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) select;
            collectSourceTablesFromFromItem(plainSelect.getFromItem(), tables);
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    collectSourceTablesFromFromItem(join.getRightItem(), tables);
                }
            }
            return;
        }
        if (select instanceof ParenthesedSelect) {
            collectSourceTables(((ParenthesedSelect) select).getSelect(), tables);
            return;
        }
        if (select instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) select;
            if (setOperationList.getSelects() != null) {
                for (Select branch : setOperationList.getSelects()) {
                    collectSourceTables(branch, tables);
                }
            }
        }
    }

    private void collectSourceTablesFromFromItem(FromItem fromItem, Set<RefTab> tables) {
        if (fromItem == null || tables == null) {
            return;
        }
        if (fromItem instanceof Table) {
            tables.add(toRefTab((Table) fromItem));
            return;
        }
        if (fromItem instanceof ParenthesedSelect) {
            collectSourceTables(((ParenthesedSelect) fromItem).getSelect(), tables);
            return;
        }
        if (fromItem instanceof ParenthesedFromItem) {
            collectSourceTablesFromFromItem(((ParenthesedFromItem) fromItem).getFromItem(), tables);
        }
    }

    private boolean isSetOperationSelect(Select select) {
        if (select == null) {
            return false;
        }
        if (select instanceof SetOperationList) {
            return true;
        }
        if (select instanceof ParenthesedSelect) {
            return isSetOperationSelect(((ParenthesedSelect) select).getSelect());
        }
        return false;
    }

    private void collectDerivedColumns(Select select, Map<String, Set<RefCol>> colMap, ParseSession session) {
        if (select instanceof PlainSelect) {
            collectDerivedColumnsFromPlainSelect((PlainSelect) select, colMap, session);
            return;
        }
        if (select instanceof ParenthesedSelect) {
            collectDerivedColumns(((ParenthesedSelect) select).getSelect(), colMap, session);
            return;
        }
        if (select instanceof SetOperationList) {
            collectDerivedColumnsFromSetOperation((SetOperationList) select, colMap, session);
        }
    }

    private void collectDerivedColumnsFromPlainSelect(PlainSelect plainSelect, Map<String, Set<RefCol>> colMap, ParseSession session) {
        RefTab inferredPrefix = inferSingleTablePrefix(plainSelect);
        Map<String, RefTab> localAlias2Table = buildLocalAliasTableMap(plainSelect);
        if (plainSelect.getSelectItems() == null) {
            return;
        }
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            String outName = resolveSelectItemOutputName(item);
            if (outName == null || outName.isEmpty()) {
                continue;
            }
            Set<RefCol> sourceSet = resolveSelectItemSourceSet(item, outName, inferredPrefix, localAlias2Table, session);
            if (!sourceSet.isEmpty()) {
                addDerivedCandidates(colMap, outName, sourceSet);
            }
        }
    }

    private void collectDerivedColumnsFromSetOperation(SetOperationList setOperationList, Map<String, Set<RefCol>> colMap, ParseSession session) {
        if (setOperationList.getSelects() == null || setOperationList.getSelects().isEmpty()) {
            return;
        }
        // UNION / SET 操作场景下，遍历所有分支。
        // 相同输出列名后续出现分支覆盖，避免仅使用第一个分支导致来源表偏差。
        for (Select branch : setOperationList.getSelects()) {
            collectDerivedColumns(branch, colMap, session);
        }
    }

    private String resolveSelectItemOutputName(SelectItem<?> item) {
        if (item == null) {
            return null;
        }
        Expression expression = item.getExpression();
        if (item.getAlias() != null && item.getAlias().getName() != null) {
            return sanitizeIdentifier(item.getAlias().getName());
        }
        if (expression instanceof Column) {
            return sanitizeIdentifier(((Column) expression).getColumnName());
        }
        return null;
    }

    private Set<RefCol> resolveSelectItemSourceSet(SelectItem<?> item, String outName, RefTab inferredPrefix, Map<String, RefTab> localAlias2Table, ParseSession session) {
        Set<RefCol> sourceSet = new LinkedHashSet<>();
        if (item == null) {
            return sourceSet;
        }
        Expression expression = item.getExpression();
        if (expression instanceof Column) {
            RefCol source = resolveColumnByLocalAlias((Column) expression, localAlias2Table, session);
            if (source != null) {
                // UNION 分支表里未带来前缀的列 (如 agw.seq_id)
                // 若直接保留 null prefix，后续可能被错误回填到外层同名列。
                // 这里优先补上当前分支主表前缀，避免归属到其它来源。
                if (source.getPrefix() == null && inferredPrefix != null) {
                    source = new RefCol(inferredPrefix, sanitizeIdentifier(((Column) expression).getColumnName()), null);
                }
                // 与 AntlrParser 对齐：列列出且带别名时，派生列应存储为输出别名。
                // 仅沿用来源表前缀（例如 market.id AS market_id 为 t_credit_debits.end_market）
                if (item.getAlias() != null && item.getAlias().getName() != null) {
                    sourceSet.add(new RefCol(source.getPrefix(), outName, null));
                } else {
                    sourceSet.add(source);
                }
            }
            return sourceSet;
        }
        Column firstColumn = findFirstColumn(expression);
        if (firstColumn != null) {
            RefCol firstRef = new RefCol();
            RefTab sourcePrefix = firstRef.getPrefix() == null ? inferredPrefix : firstRef.getPrefix();
            // 对函数/计算列场景，保留“表达式到列来源”候选
            sourceSet.add(new RefCol(sourcePrefix, outName, null));
            // 同时补充“主表来源”候选，避免派生列稳定归属到次表（与 Antlr 行为对齐）
            if (inferredPrefix != null) {
                sourceSet.add(new RefCol(inferredPrefix, outName, null));
            }
        }
        return sourceSet;
    }

    private void addDerivedCandidates(Map<String, Set<RefCol>> colMap, String outName, Set<RefCol> candidates) {
        if (outName == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        String key = outName.toLowerCase(Locale.ROOT);
        Set<RefCol> current = colMap.getOrDefault(key, new LinkedHashSet<>());
        current.addAll(candidates);
        colMap.put(key, current);
    }

    private Column findFirstColumn(Expression expression) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof Column) {
            return (Column) expression;
        }
        if (expression instanceof Parenthesis) {
            return findFirstColumn(((Parenthesis) expression).getExpression());
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) expression;
            Column left = findFirstColumn(be.getLeftExpression());
            if (left != null) {
                return left;
            }
            return findFirstColumn(be.getRightExpression());
        }
        if (expression instanceof Function) {
            Function function = (Function) expression;
            if (function.getParameters() != null) {
                for (Expression arg : function.getParameters()) {
                    Column col = findFirstColumn(arg);
                    if (col != null) {
                        return col;
                    }
                }
            }
        }
        if (expression instanceof InExpression) {
            return findFirstColumn(((InExpression) expression).getLeftExpression());
        }
        if (expression instanceof Between) {
            return findFirstColumn(((Between) expression).getLeftExpression());
        }
        return null;
    }

    private Map<String, RefTab> buildLocalAliasTableMap(PlainSelect plainSelect) {
        Map<String, RefTab> map = new HashMap<>();
        if (plainSelect == null) {
            return map;
        }
        if (plainSelect.getFromItem() != null) {
            registerAliasFromFromItem(plainSelect.getFromItem(), map);
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                registerAliasFromFromItem(join.getRightItem(), map);
            }
        }
        return map;
    }

    private void registerAliasFromFromItem(FromItem fromItem, Map<String, RefTab> map) {
        if (fromItem instanceof ParenthesedFromItem) {
            ParenthesedFromItem parenthesedFromItem = (ParenthesedFromItem) fromItem;
            if (parenthesedFromItem.getAlias() != null
                    && parenthesedFromItem.getAlias().getName() != null) {
                String alias = parenthesedFromItem.getAlias().getName().toLowerCase(Locale.ROOT);
                map.put(alias, new RefTab(parenthesedFromItem.getAlias().getName()));
            }
            return;
        }
        if (!(fromItem instanceof Table)) {
            return;
        }
        Table table = (Table) fromItem;
        if (table.getAlias() == null || table.getAlias().getName() == null) {
            return;
        }
        String alias = table.getAlias().getName().toLowerCase(Locale.ROOT);
        map.put(alias, toRefTab(table));
    }

    private RefCol resolveColumnByLocalAlias(Column column, Map<String, RefTab> localAlias2Table, ParseSession session) {
        if (column == null) {
            return null;
        }
        if (column.getTable() != null && column.getTable().getName() != null && !column.getTable().getName().isEmpty()) {
            String alias = column.getTable().getName().toLowerCase(Locale.ROOT);
            if (localAlias2Table != null && localAlias2Table.containsKey(alias)) {
                RefTab realTab = localAlias2Table.get(alias);
                return new RefCol(realTab, sanitizeIdentifier(column.getColumnName()), null);
            }
        }
        // 本地映射缺失时，尝试使用已注册的派生别名映射（如 d.xxx / c.xxx）
        String alias = column.getTable() == null ? null : column.getTable().getName();
        if (alias != null) {
            alias = alias.toLowerCase(Locale.ROOT);
        }
        if (alias != null && session.derivedAliasRegistry.columnMap().containsKey(alias)) {
            Map<String, Set<RefCol>> colMap = session.derivedAliasRegistry.columnMap().get(alias);
            Set<RefCol> mapped = colMap.get(session.columnResolver.normalizeColumnKey(column));
            RefCol canonical = pickCanonicalRefCol(mapped);
            if (canonical != null) {
                return new RefCol(canonical.getPrefix(), canonical.getName(), null);
            }
        }
        return toRefCol(column, session);
    }

    private RefTab inferSingleTablePrefix(PlainSelect plainSelect) {
        if (plainSelect == null || plainSelect.getFromItem() == null) {
            return null;
        }
        if (!(plainSelect.getFromItem() instanceof Table)) {
            return null;
        }
        Table fromTable = (Table) plainSelect.getFromItem();
        // 派生列归属需要稳定的“中真实表”，避免别名（如 b）在后续评分阶段丢失语义
        return toRefTab(fromTable);
    }

    private void convertSelectItem(SelectItem<?> selectItem, SQLContext context, ParseSession session) {
        Expression expression = selectItem.getExpression();
        if (expression instanceof AllColumns) {
            context.addColumn(new RefCol(null, "*", null));
            return;
        }
        if (expression instanceof AllTableColumns) {
            AllTableColumns allTableColumns = (AllTableColumns) expression;
            RefTab table = toRefTab(allTableColumns.getTable());
            context.addColumn(new RefCol(table, "*", null));
            return;
        }
        extractColumns(expression, context, session);
        // AntlrParser 会把 select item 里的 CASE 条件也纳入约束（例如 CASE WHEN col < x THEN ...）
        // 这里补充该行为，避免遗漏 range/equal 等条件
        extractConstraints(expression, context, session);
    }

    private void extractColumns(Expression expression, SQLContext context, ParseSession session) {
        if (expression == null) {
            return;
        }
        if (expression instanceof Parenthesis) {
            extractColumns(((Parenthesis) expression).getExpression(), context, session);
            return;
        }
        if (expression instanceof Select) {
            // select item 中嵌套子查询：递归提取表、字段与约束
            mergeSubqueryContext((Select) expression, context, session);
            return;
        }
        if (expression instanceof Column) {
            context.addColumn(toRefCol((Column) expression, session));
            return;
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            extractColumns(binaryExpression.getLeftExpression(), context, session);
            extractColumns(binaryExpression.getRightExpression(), context, session);
            return;
        }
        if (expression instanceof InExpression) {
            InExpression inExpression = (InExpression) expression;
            extractColumns(inExpression.getLeftExpression(), context, session);
            return;
        }
        if (expression instanceof Function) {
            Function function = (Function) expression;
            if (function.getParameters() != null) {
                for (Expression arg : function.getParameters()) {
                    extractColumns(arg, context, session);
                }
            }
            return;
        }
        if (expression instanceof Between) {
            Between between = (Between) expression;
            extractColumns(between.getLeftExpression(), context, session);
            return;
        }
    }

    private void extractConstraints(Expression expression, SQLContext context, ParseSession session) {
        if (expression == null) {
            return;
        }
        if (expression instanceof CaseExpression) {
            CaseExpression caseExpression = (CaseExpression) expression;
            handleSimpleCaseExpression(caseExpression, context, session);
            if (caseExpression.getSwitchExpression() != null) {
                extractConstraints(caseExpression.getSwitchExpression(), context, session);
            }
            if (caseExpression.getWhenClauses() != null) {
                for (WhenClause whenClause : caseExpression.getWhenClauses()) {
                    if (whenClause.getWhenExpression() != null) {
                        extractConstraints(whenClause.getWhenExpression(), context, session);
                    }
                    if (whenClause.getThenExpression() != null) {
                        extractConstraints(whenClause.getThenExpression(), context, session);
                    }
                }
            }
            if (caseExpression.getElseExpression() != null) {
                extractConstraints(caseExpression.getElseExpression(), context, session);
            }
            return;
        }
        if (expression instanceof Parenthesis) {
            extractConstraints(((Parenthesis) expression).getExpression(), context, session);
            return;
        }
        if (expression instanceof AndExpression) {
            AndExpression andExpression = (AndExpression) expression;
            extractConstraints(andExpression.getLeftExpression(), context, session);
            extractConstraints(andExpression.getRightExpression(), context, session);
            return;
        }
        if (expression instanceof OrExpression) {
            OrExpression orExpression = (OrExpression) expression;
            extractConstraints(orExpression.getLeftExpression(), context, session);
            extractConstraints(orExpression.getRightExpression(), context, session);
            return;
        }
        if (expression instanceof Between) {
            handleBetween((Between) expression, context, session);
            return;
        }
        if (expression instanceof InExpression) {
            handleInExpression((InExpression) expression, context, session);
            return;
        }
        if (expression instanceof LikeExpression) {
            handleLikeExpression((LikeExpression) expression, context, session);
            return;
        }
        if (expression instanceof IsNullExpression) {
            handleIsNullExpression((IsNullExpression) expression, context, session);
            return;
        }
        if (session.constraintExtractor.isBinaryComparison(expression)) {
            handleBinaryComparison((BinaryExpression) expression, context, session);
        }
    }

    private void handleSimpleCaseExpression(CaseExpression caseExpression, SQLContext context, ParseSession session) {
        if (caseExpression == null || !(caseExpression.getSwitchExpression() instanceof Column)) {
            return;
        }
        if (caseExpression.getWhenClauses() == null || caseExpression.getWhenClauses().isEmpty()) {
            return;
        }
        RefCol switchCol = toRefCol((Column) caseExpression.getSwitchExpression(), session);
        ColumnConstraint constraint = new ColumnConstraint();
        for (WhenClause whenClause : caseExpression.getWhenClauses()) {
            if (whenClause == null || whenClause.getThenExpression() == null) {
                continue;
            }
            // simple-case 的 WHEN 常量视作 switch 列的筛选取值集合
            Expression whenExpr = whenClause.getWhenExpression();
            if (whenExpr instanceof Column || whenExpr instanceof Select) {
                continue;
            }
            String val = String.valueOf(whenExpr).trim();
            if (!val.isEmpty()) {
                constraint.addEqual(val);
            }
        }
        if (!constraint.getEquals().isEmpty()) {
            context.addColumn(switchCol);
            context.addConstraint(switchCol, constraint);
        }
    }

    private void handleBetween(Between between, SQLContext context, ParseSession session) {
        if (!(between.getLeftExpression() instanceof Column)) {
            return;
        }
        RefCol left = toRefCol((Column) between.getLeftExpression(), session);
        context.addColumn(left);
        ColumnConstraint constraint = new ColumnConstraint();
        String start = between.getBetweenExpressionStart() != null ? between.getBetweenExpressionStart().toString() : null;
        String end = between.getBetweenExpressionEnd() != null ? between.getBetweenExpressionEnd().toString() : null;
        constraint.addRange(new StringRange(start, end));
        context.addConstraint(left, constraint);
    }

    private void handleInExpression(InExpression inExpression, SQLContext context, ParseSession session) {
        if (!(inExpression.getLeftExpression() instanceof Column)) {
            return;
        }
        RefCol left = toRefCol((Column) inExpression.getLeftExpression(), session);
        context.addColumn(left);
        ColumnConstraint constraint = new ColumnConstraint();
        if (inExpression.getRightExpression() instanceof ExpressionList<?>) {
            ExpressionList<?> expressionList = (ExpressionList<?>) inExpression.getRightExpression();
            for (Object item : expressionList.getExpressions()) {
                if (inExpression.isNot()) {
                    // constraint.addNotInConstraint(String.valueOf(item));
                } else {
                    constraint.addEqual(String.valueOf(item));
                }
            }
            context.addConstraint(left, constraint);
            return;
        }
        if (inExpression.getRightExpression() instanceof Select) {
            Select subSelect = (Select) inExpression.getRightExpression();
            // 子查询中的 where/join 约束也要纳入上下文
            mergeSubqueryContext(subSelect, context, true, session);
            // 与 Antlr 输出对齐: IN (SELECT col ...) 记为 left = subquery.col 的关联约束
            RefCol right = extractSubqueryAssociationColumn(subSelect, session);
            if (right != null) {
                context.addColumn(right);
                constraint.addRelate(new Relate("=", right));
                context.addConstraint(left, constraint);
            }
        }
    }

    private RefCol extractSubqueryAssociationColumn(Select select, ParseSession session) {
        if (select == null) {
            return null;
        }
        if (select instanceof ParenthesedSelect) {
            return extractSubqueryAssociationColumn(((ParenthesedSelect) select).getSelect(), session);
        }
        if (select instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) select;
            if (setOperationList.getSelects() == null || setOperationList.getSelects().isEmpty()) {
                return null;
            }
            // 与 set-op 其他归属策略保持一致：优先最后分支
            return extractSubqueryAssociationColumn(setOperationList.getSelects().get(setOperationList.getSelects().size() - 1), session);
        }
        if (!(select instanceof PlainSelect)) {
            return null;
        }
        PlainSelect plainSelect = (PlainSelect) select;
        if (plainSelect.getSelectItems() == null || plainSelect.getSelectItems().isEmpty()) {
            return null;
        }
        SelectItem<?> first = plainSelect.getSelectItems().get(0);
        if (!(first.getExpression() instanceof Column)) {
            return null;
        }
        Column column = (Column) first.getExpression();
        if (column.getTable() != null && column.getTable().getName() != null && !column.getTable().getName().isEmpty()) {
            return toRefCol(column, session);
        }
        RefTab inferred = inferSingleTablePrefix(plainSelect);
        if (inferred != null) {
            return new RefCol(inferred, sanitizeIdentifier(column.getColumnName()), null);
        }
        return toRefCol(column, session);
    }

    private void handleLikeExpression(LikeExpression likeExpression, SQLContext context, ParseSession session) {
        if (!(likeExpression.getLeftExpression() instanceof Column)) {
            return;
        }
        RefCol left = toRefCol((Column) likeExpression.getLeftExpression(), session);
        context.addColumn(left);
        ColumnConstraint constraint = new ColumnConstraint();
        if (likeExpression.isNot()) {
            // constraint.addNotLikeConstraint(likeExpression.getRightExpression().toString());
        } else {
            constraint.addEqual(likeExpression.getRightExpression().toString());
        }
        context.addConstraint(left, constraint);
    }

    private void handleBinaryComparison(BinaryExpression expression, SQLContext context, ParseSession session) {
        Expression leftExpression = expression.getLeftExpression();
        Expression rightExpression = expression.getRightExpression();
        String operator = expression.getStringExpression();
        if (handleInstrComparison(leftExpression, rightExpression, operator, context, session)) {
            return;
        }
        if (leftExpression instanceof Column) {
            if (rightExpression instanceof Column) {
                handleColumnToColumnComparison((Column) leftExpression, (Column) rightExpression, operator, context, session);
            } else {
                handleColumnToLiteralComparison((Column) leftExpression, rightExpression, operator, context, session);
            }
            return;
        }
        if (rightExpression instanceof Column) {
            handleLiteralToColumnComparison(leftExpression, (Column) rightExpression, operator, context, session);
        }
    }

    private void handleColumnToColumnComparison(Column leftColumnExpr, Column rightColumnExpr, String operator, SQLContext context, ParseSession session) {
        Set<RefCol> leftCandidates = resolveRefColCandidates(leftColumnExpr, session);
        Set<RefCol> rightCandidates = resolveRefColCandidates(rightColumnExpr, session);
        boolean leftDerived = isDerivedAliasColumn(leftColumnExpr, session);
        boolean rightDerived = isDerivedAliasColumn(rightColumnExpr, session);
        // UNION 派生列在左侧且多分支时：按分支分别落约束（如 x.market_id = d.market_id -> 两表均关联）
        if (isSetOpDerivedAliasColumn(leftColumnExpr, session) && leftCandidates.size() > 1) {
            for (RefCol lk : leftCandidates) {
                context.addColumn(lk);
                ColumnConstraint constraint = new ColumnConstraint();
                addRelateTargets(constraint, rightCandidates, rightColumnExpr, operator, context, session);
                context.addConstraint(lk, constraint);
            }
            return;
        }
        RefCol leftColumn = toRefCol(leftColumnExpr, session);
        // 一侧为派生别名、一侧为真实表时：仅当「左侧」为派生时用 canonical，避免把左右列 RefCol 搞混
        // 混流 union 再 pickCanonical（会把 ON 右侧的如 t.subaccountlogicid 误当作 column2constraint 的 key，
        // 出现 appsubcountposts.logicid = t.subaccountlogicid 都落到错误 RefCol / 相似表的问题）。
        if (leftDerived ^ rightDerived) {
            boolean multiUnionOtherSide = isSetOpDerivedAliasColumn(rightColumnExpr, session) && rightCandidates.size() > 1;
            if (!multiUnionOtherSide && leftDerived) {
                RefCol normalized = pickCanonicalRefCol(leftCandidates);
                if (normalized != null) {
                    leftColumn = normalized;
                }
            }
        }
        context.addColumn(leftColumn);
        ColumnConstraint constraint = new ColumnConstraint();
        addRelateTargets(constraint, rightCandidates, rightColumnExpr, operator, context, session);
        context.addConstraint(leftColumn, constraint);
    }

    private void handleColumnToLiteralComparison(Column leftColumnExpr, Expression rightExpression, String operator, SQLContext context, ParseSession session) {
        Set<RefCol> leftCandidates = resolveRefColCandidates(leftColumnExpr, session);
        if (isSetOpDerivedAliasColumn(leftColumnExpr, session) && leftCandidates.size() > 1) {
            for (RefCol candidate : leftCandidates) {
                context.addColumn(candidate);
                ColumnConstraint constraint = new ColumnConstraint();
                constraint.addConstraint(operator, rightExpression.toString());
                context.addConstraint(candidate, constraint);
            }
            return;
        }
        RefCol leftColumn = toRefCol(leftColumnExpr, session);
        context.addColumn(leftColumn);
        ColumnConstraint constraint = new ColumnConstraint();
        constraint.addConstraint(operator, rightExpression.toString());
        context.addConstraint(leftColumn, constraint);
    }

    private void handleLiteralToColumnComparison(Expression leftExpression, Column rightColumnExpr, String operator, SQLContext context, ParseSession session) {
        // 常量在左、列在右时，将操作符反转后记录到右侧列
        Set<RefCol> rightCandidates = resolveRefColCandidates(rightColumnExpr, session);
        if (rightCandidates.isEmpty()) {
            RefCol rightColumn = toRefCol(rightColumnExpr, session);
            context.addColumn(rightColumn);
            ColumnConstraint constraint = new ColumnConstraint();
            constraint.addConstraint(reverseOperator(operator), leftExpression.toString());
            context.addConstraint(rightColumn, constraint);
            return;
        }
        for (RefCol rightColumn : rightCandidates) {
            context.addColumn(rightColumn);
            ColumnConstraint constraint = new ColumnConstraint();
            constraint.addConstraint(reverseOperator(operator), leftExpression.toString());
            context.addConstraint(rightColumn, constraint);
        }
    }

    private void addRelateTargets(ColumnConstraint constraint, Set<RefCol> rightCandidates, Column rightColumnExpr, String operator, SQLContext context, ParseSession session) {
        if (rightCandidates == null || rightCandidates.isEmpty()) {
            RefCol rightColumn = toRefCol(rightColumnExpr, session);
            context.addColumn(rightColumn);
            constraint.addRelate(new Relate(operator, rightColumn));
            return;
        }
        for (RefCol rightColumn : rightCandidates) {
            context.addColumn(rightColumn);
            constraint.addRelate(new Relate(operator, rightColumn));
        }
    }

    private boolean isDerivedAliasColumn(Column column, ParseSession session) {
        if (column == null || column.getTable() == null || column.getTable().getName() == null) {
            return false;
        }
        String alias = column.getTable().getName().toLowerCase(Locale.ROOT);
        return session.derivedAliasRegistry.columnMap().containsKey(alias);
    }

    /** 判断列是否为 UNION/SET 运算的派生别名表（如 x 来自 t_securities UNION t_all_securities）. */
    private boolean isSetOpDerivedAliasColumn(Column column, ParseSession session) {
        if (column == null || column.getTable() == null || column.getTable().getName() == null) {
            return false;
        }
        String alias = column.getTable().getName().toLowerCase(Locale.ROOT);
        return Boolean.TRUE.equals(session.derivedAliasRegistry.fromSetOpMap().get(alias));
    }

    private void handleIsNullExpression(IsNullExpression expression, SQLContext context, ParseSession session) {
        if (!(expression.getLeftExpression() instanceof Column)) {
            return;
        }
        RefCol left = toRefCol((Column) expression.getLeftExpression(), session);
        context.addColumn(left);
        ColumnConstraint constraint = new ColumnConstraint();
        if (expression.isNot()) {
            // constraint.addNotEqualConstraint("null");
        } else {
            constraint.addEqual("null");
        }
        context.addConstraint(left, constraint);
    }

    private boolean handleInstrComparison(Expression leftExpression, Expression rightExpression, String operator, SQLContext context, ParseSession session) {
        if (!(leftExpression instanceof Function)) {
            return false;
        }
        if (!">".equals(operator) && !">=".equals(operator)) {
            return false;
        }
        if (!"0".equals(String.valueOf(rightExpression).trim())) {
            return false;
        }
        Function function = (Function) leftExpression;
        if (function.getName() == null || !"INSTR".equalsIgnoreCase(function.getName())) {
            return false;
        }
        if (function.getParameters() == null || function.getParameters().size() != 2) {
            return false;
        }
        Expression firstArg = function.getParameters().get(0);
        Expression secondArg = function.getParameters().get(1);
        if (!(secondArg instanceof Column)) {
            return false;
        }
        RefCol column = toRefCol((Column) secondArg, session);
        context.addColumn(column);
        String raw = String.valueOf(firstArg).trim();
        if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\""))) {
            raw = raw.substring(1, raw.length() - 1);
            ColumnConstraint constraint = new ColumnConstraint();
            for (String item : raw.split("\\|")) {
                String val = item.trim();
                // INSTR('all', col) 常用于“不过滤”的占位，不应当成真实 IN 条件
                if (!val.isEmpty() && !"all".equalsIgnoreCase(val)) {
                    constraint.addEqual(val);
                }
            }
            if (!constraint.getEquals().isEmpty()) {
                context.addConstraint(column, constraint);
                return true;
            }
        }
        return false;
    }

    private String reverseOperator(String operator) {
        String normalized = operator == null ? "" : operator.toUpperCase(Locale.ROOT);
        switch (normalized) {
            case ">":
                return "<";
            case ">=":
                return "<=";
            case "<":
                return ">";
            case "<=":
                return ">=";
            default:
                return operator;
        }
    }

    private RefCol toRefCol(Column column, ParseSession session) {
        Set<RefCol> candidates = resolveRefColCandidates(column, session);
        RefCol canonical = pickCanonicalRefCol(candidates);
        if (canonical != null) {
            return canonical;
        }
        if (column.getTable() != null && column.getTable().getName() != null && !column.getTable().getName().isEmpty()) {
            RefTab table = toRefTab(column.getTable());
            return new RefCol(table, sanitizeIdentifier(column.getColumnName()), null);
        }
        return new RefCol(null, sanitizeIdentifier(column.getColumnName()), null);
    }

    private RefTab resolveScopedAliasTable(String alias, ParseSession session) {
        if (alias == null || alias.isEmpty() || session.derivedAliasRegistry.aliasScopeStack().isEmpty()) {
            return null;
        }
        for (Map<String, RefTab> scope : session.derivedAliasRegistry.aliasScopeStack()) {
            if (scope == null) {
                continue;
            }
            RefTab hit = scope.get(alias);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private Set<RefCol> resolveRefColCandidates(Column column, ParseSession session) {
        Set<RefCol> result = new LinkedHashSet<>();
        if (column == null) {
            return result;
        }
        if (column.getTable() != null && column.getTable().getName() != null && !column.getTable().getName().isEmpty()) {
            String tableName = column.getTable().getName();
            String scopedAlias = tableName.toLowerCase(Locale.ROOT);
            RefTab scopedTable = resolveScopedAliasTable(scopedAlias, session);
            if (scopedTable != null) {
                result.add(new RefCol(scopedTable, sanitizeIdentifier(column.getColumnName()), null));
                return result;
            }
            String key = tableName.toLowerCase(Locale.ROOT);
            if (session.derivedAliasRegistry.columnMap().containsKey(key)) {
                Map<String, Set<RefCol>> colMap = session.derivedAliasRegistry.columnMap().get(key);
                Set<RefCol> mappedSet = colMap.get(session.columnResolver.normalizeColumnKey(column));
                if (mappedSet != null) {
                    // UNION / SET 派生列：保留各分支展开后的来源（如 t_securities 与 t_all_securities）
                    // 便于 ON 条件上记录与真实表实例的关联；canonical 评分仅用于 toRefCol 等简单 key 场景
                    for (RefCol mapped : mappedSet) {
                        result.addAll(resolveDerivedRefColCandidates(mapped, new HashSet<>(), session));
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
            if (session.derivedAliasRegistry.sourceTablesMap().containsKey(key)) {
                String colName = sanitizeIdentifier(column.getColumnName());
                for (RefTab sourceTab : session.derivedAliasRegistry.sourceTablesMap().get(key)) {
                    result.add(new RefCol(sourceTab, colName, null));
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        RefTab table = toRefTab(column.getTable());
        result.add(new RefCol(table, sanitizeIdentifier(column.getColumnName()), null));
        // 无前缀列优先归属当前 select 的主 from 表，避免生成裸列 key（如 report_type）
        result.add(new RefCol(null, sanitizeIdentifier(column.getColumnName()), null));
        return result;
    }

    private Set<RefCol> resolveDerivedRefColCandidates(RefCol refCol, Set<String> visiting, ParseSession session) {
        Set<RefCol> result = new LinkedHashSet<>();
        if (refCol == null || refCol.getPrefix() == null || refCol.getPrefix().getName() == null) {
            result.add(refCol);
            return result;
        }
        String alias = refCol.getPrefix().getName().toLowerCase(Locale.ROOT);
        String colName = refCol.getName() == null ? "" : refCol.getName().toLowerCase(Locale.ROOT);
        String visitKey = alias + "." + colName;
        if (visiting.contains(visitKey)) {
            result.add(refCol);
            return result;
        }
        Map<String, Set<RefCol>> colMap = session.derivedAliasRegistry.columnMap().get(alias);
        if (colMap == null) {
            result.add(refCol);
            return result;
        }
        Set<RefCol> mappedSet = colMap.get(colName);
        if (mappedSet == null || mappedSet.isEmpty()) {
            result.add(refCol);
            return result;
        }
        visiting.add(visitKey);
        for (RefCol mapped : mappedSet) {
            RefCol next = new RefCol(mapped.getPrefix(), mapped.getName(), null);
            result.addAll(resolveDerivedRefColCandidates(next, visiting, session));
        }
        visiting.remove(visitKey);
        return result;
    }

    private RefCol pickCanonicalRefCol(Set<RefCol> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        RefCol best = null;
        int bestScore = Integer.MIN_VALUE;
        for (RefCol candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            int score = scoreRefCol(candidate);
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private int scoreRefCol(RefCol candidate) {
        if (candidate == null || candidate.getPrefix() == null || candidate.getPrefix().getName() == null) {
            return -1000;
        }
        String table = candidate.getPrefix().getName().toLowerCase(Locale.ROOT);
        String col = candidate.getName() == null ? "" : candidate.getName().toLowerCase(Locale.ROOT);

        // 通用评分，避免业务表名硬编码：
        // 1) 有效前缀优先（已在上面过滤）
        // 2) 显式业务表命名（t_xxx）优先于临时/派生别名
        // 3) 列名非空优先
        // 4) 稳定的轻量区分，降低完全同分概率
        int score = 0;
        if (table.startsWith("t_")) {
            score += 100;
        } else {
            score += 60;
        }
        if (!col.isEmpty()) {
            score += 20;
        }
        score += Math.min(table.length(), 20);
        return score;
    }

    private void mergeSubqueryContext(Select subSelect, SQLContext parentContext, ParseSession session) {
        mergeSubqueryContext(subSelect, parentContext, true, session);
    }

    private void mergeSubqueryContext(Select subSelect, SQLContext parentContext, boolean mergeConstraints, ParseSession session) {
        if (subSelect == null) {
            return;
        }
        if (mergeConstraints) {
            SQLContext childForConstraint = new SQLContext();
            Select constraintSelect = getConstraintSelect(subSelect);
            convertSelectBody(constraintSelect, childForConstraint, session);
            // 只合并约束，避免子查询别名映射覆盖父级 alias 作用域
            parentContext.merge(childForConstraint, false, false);
        }
        SQLContext childContext = new SQLContext();
        convertSelectBody(subSelect, childContext, session);
        // 表信息单并入，且不带 alias，防止名字冲突
        appendTablesWithoutAlias(childContext.getRefTables(), parentContext);
    }

    private Select getConstraintSelect(Select subSelect) {
        if (subSelect instanceof ParenthesedSelect) {
            return getConstraintSelect(((ParenthesedSelect) subSelect).getSelect());
        }
        if (subSelect instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) subSelect;
            if (setOperationList.getSelects() != null && !setOperationList.getSelects().isEmpty()) {
                // 经验对齐策略:
                // - 分支较少 (<=2) 时合并全部分支，避免遗漏分支约束
                // - 分支较多时取最后分支，避免父级约束取值漂移
                if (setOperationList.getSelects().size() <= 2) {
                    return subSelect;
                }
                return getConstraintSelect(setOperationList.getSelects().get(setOperationList.getSelects().size() - 1));
            }
        }
        return subSelect;
    }

    private void appendTablesWithoutAlias(Set<RefTab> source, SQLContext target) {
        if (source == null || target == null) {
            return;
        }
        for (RefTab tab : source) {
            if (tab == null || tab.getName() == null) {
                continue;
            }
            RefTab clean = new RefTab(tab.getPrefix(), tab.getName(), null);
            target.addTable(clean);
        }
    }

    private RefTab toRefTab(Table table) {
        if (table == null) {
            return null;
        }
        String schema = table.getSchemaName();
        String tableName = table.getName();
        return new RefTab(sanitizeIdentifier(schema), sanitizeIdentifier(tableName), null);
    }

    private String sanitizeIdentifier(String name) {
        if (name == null) {
            return null;
        }
        String ans = name.trim();
        if (ans.length() >= 2) {
            char first = ans.charAt(0);
            char last = ans.charAt(ans.length() - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                ans = ans.substring(1, ans.length() - 1);
            }
        }
        return ans;
    }

}