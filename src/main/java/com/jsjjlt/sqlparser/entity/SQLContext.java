package com.jsjjlt.sqlparser.entity;

import com.jsjjlt.sqlparser.constraint.ColumnConstraint;
import com.jsjjlt.sqlparser.constraint.TableConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

@Data
@AllArgsConstructor
public class SQLContext {
    static Logger logger = LogManager.getLogger(SQLContext.class);
    public static final int QUERY = 1001;
    public static final int UPDATE = 1002;
    public static final int INSERT = 1003;
    public static final int DELETE = 1004;
    public static final int UNDEFINED = 9999;

    private Set<RefCol> columns;
    private Set<RefTab> tables;
    private Set<RefTab> allTables;
    private Map<RefCol, ColumnConstraint> column2constraint;
    private Map<String, RefCol> alias2column;
    private Map<String, RefTab> alias2table;
    private RefTab global_tab;
    private ColumnConstraint tempConstraint;
    private int queryType = UNDEFINED;

    public SQLContext() {
        columns = new HashSet<>();
        tables = new HashSet<>();
        column2constraint = new HashMap<>();
        alias2column = new HashMap<>();
        alias2table = new HashMap<>();
        allTables = new HashSet<>();
    }

    public void merge(SQLContext ores) {
        merge(ores, true, true);
    }

    public void addTable(RefTab table) {
        this.tables.add(table);
        this.allTables.add(table);
        if (table.getAlias() != null) {
            for (String alias : table.getAlias()) {
                alias2table.put(alias, table);
            }
        }
    }

    public void addTableToRefTab(net.sf.jsqlparser.schema.Table table) {
        if (table == null) {
            return;
        }
        RefTab refTab = new RefTab(table.getSchemaName(), table.getName(), null);
        if (table.getAlias() != null && table.getAlias().getName() != null) {
            refTab.addAlias(table.getAlias().getName());
        }
        addTable(refTab);
    }

    public void addColumn(RefCol column) {
        column = repairColumn(column);
        this.columns.add(column);
        if (column != null && column.getAlias() != null) {
            for (String alias : column.getAlias()) {
                alias2column.put(column.getName() + alias, column);
            }
        }
        if (column != null && column.getName().equals("*")) {
            global_tab = column.getPrefix();
        }
    }

    public void addConstraint(RefCol column, ColumnConstraint constraint) {
        column = repairColumn(column);
        if (column == null || constraint == null) return;
        for (Relate item : constraint.getRelates()) {
            RefCol refCol = item.getColumn();
            item.setColumn(repairColumn(refCol));
        }
        if (column2constraint.containsKey(column)) {
            ColumnConstraint tempConstraint = column2constraint.get(column);
            tempConstraint.merge(constraint);
        } else {
            column2constraint.put(column, constraint);
        }
    }

    public void addAlias(String alias) {
        for (RefCol refCol : columns) {
            if (refCol.getPrefix() != null) {
                refCol.getPrefix().addAlias(alias);
                alias2table.put(alias, refCol.getPrefix());
            }
        }
    }

    public void merge(SQLContext context, boolean isMergeSelectItem, boolean isMergeTable) {
        Map<String, RefCol> name2column = new HashMap<>();
        for (RefCol column : columns) {
            if (column.getPrefix() != null) {
                name2column.put(column.getPrefix().getName() + "." + column.getName(), column);
            }
        }
        for (RefCol column : context.getColumns()) {
            if (column.getPrefix() != null && name2column.containsKey(column.getPrefix().getName() + "." + column.getName())) {
                name2column.get(column.getPrefix().getName() + "." + column.getName()).addAlias(column.getAlias());
            } else {
                addColumn(column);
            }
        }
        if (isMergeTable) {
            Map<String, RefTab> name2table = new HashMap<>();
            for (RefTab table : tables) {
                name2table.put(table.getName(), table);
            }
            for (RefTab table : context.getTables()) {
                if (name2table.containsKey(table.getName())) {
                    RefTab refTab = name2table.get(table.getName());
                    refTab.addAlias(table.getAlias());
                } else {
                    addTable(table);
                }
            }
        }
        allTables.addAll(context.getAllTables());
        Map<RefCol, ColumnConstraint> tempColumn2constraint = context.getColumn2constraint();
        for (RefCol refCol : tempColumn2constraint.keySet()) {
            ColumnConstraint constraint = tempColumn2constraint.get(refCol);
            Set<Relate> associationList = constraint.getRelates();
            for (Relate item : associationList) {
                RefCol column = item.getColumn();
                item.setColumn(repairColumn(column));
            }
            constraint.setRelates(associationList);
            if (column2constraint.containsKey(refCol)) {
                column2constraint.get(refCol).merge(constraint);
            } else {
                this.addConstraint(refCol, tempColumn2constraint.get(refCol));
            }
        }
    }

    public RefCol repairColumn(RefCol column) {
        if (column == null) return null;
        /* 修正 column */
        // 处理 column.name 是别名
        if (alias2column.containsKey(column.getName())) {
            RefCol tempColumn = alias2column.get(column.getName());
            tempColumn.addAlias(column.getAlias());
            column = tempColumn;
        }
        // SQL: select * from (select a.* from table1) where col1 = 1
        // 如果缺失 prefix 且 global_tab 不为 null
        if (column.getPrefix() == null && global_tab != null) {
            column.setPrefix(global_tab);
        }
        /* 修正 column 前缀 */
        RefTab refTab = column.getPrefix();
        if (refTab != null) {
            Map<String, RefTab> name2table = new HashMap<>();
            // 将 selectItems 罗列, 如果 column 为 alias.column 则替换
            for (RefCol tempColumn : columns) {
                if (tempColumn.getPrefix() != null && tempColumn.getPrefix().getAlias() != null) {
                    for (String alias : tempColumn.getPrefix().getAlias()) {
                        name2table.put(alias + "." + tempColumn.getName(), tempColumn.getPrefix());
                    }
                }
            }
            String name = refTab.getPrefix() + "." + column.getName();
            if (name2table.containsKey(name)) {
                column.setPrefix(name2table.get(name));
            } else if (alias2table.containsKey(refTab.getName())) {
                column.setPrefix(alias2table.get(refTab.getName()));
            }
        } else {
            // 当值有一个表时, context 中的所有字段前缀均为该表
            if (tables.size() == 1) {
                column.setPrefix(tables.stream().findAny().get());
            }
            for (RefCol refCol : columns) {
                if (refCol.getName().equals(column.getName()) && refCol.getPrefix() != null) {
                    column = refCol;
                }
            }
        }
        return column;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tables: ").append(tables).append("\n");
        sb.append("Table Alias: ").append(alias2table.keySet()).append("\n");
        sb.append("All Tables: ").append(allTables).append("\n");
        sb.append("Columns: [");
        for (RefCol column : columns) {
            sb.append("(").append(column).append(")");
        }
        sb.append("]\n");
        sb.append("Column Alias: ").append(alias2column.keySet()).append("\n");
        sb.append("Constraints: \n");
        for (RefCol column : column2constraint.keySet()) {
            sb.append("\t").append(column).append(" -> ").append(column2constraint.get(column)).append("\n");
        }
        return sb.toString();
    }

    public void describe() {
        logger.info("表: " + tables);
        logger.info("表 别名: " + alias2table.keySet());
        logger.info("全表: " + allTables);
        StringBuilder sb = new StringBuilder("字段: [");
        for (RefCol column : columns) {
            sb.append("(").append(column).append(")");
        }
        sb.append("]");
        logger.info(sb.toString());
        sb = new StringBuilder("字段 别名: ");
        sb.append(alias2column.keySet());
        logger.info(sb.toString());
        logger.info("约束: ");
        column2constraint.forEach((RefCol column, ColumnConstraint constraint) -> {
            logger.info("\t" + column + " -> " + constraint);
        });
    }

    public Map<RefTab, TableConstraint> getTable2constraint() {
        return getTable2constraint(null);
    }

    public Set<RefTab> getRefTables() {
        return getTables();
    }

    public Map<RefTab, TableConstraint> getTable2constraint(String prefix) {
        Map<RefTab, TableConstraint> table2constraint = new HashMap<>();
        for (Map.Entry<RefCol, ColumnConstraint> entry : column2constraint.entrySet()) {
            RefCol column = entry.getKey();
            ColumnConstraint constraint = entry.getValue();
            if (column == null || constraint == null) continue;
            RefTab table = column.getPrefix();
            if (table != null && (table.getPrefix() == null || table.getPrefix().isEmpty()) && prefix != null) {
                table = new RefTab(prefix, table.getName(), null);
            }
            TableConstraint tableConstraint = table2constraint.computeIfAbsent(table, TableConstraint::new);
            ColumnConstraint toAdd;
            if (constraint.getRefCol() != null && constraint.getRefCol().equals(column)) {
                toAdd = constraint;
            } else {
                toAdd = new ColumnConstraint(column);
                toAdd.merge(constraint);
            }
            tableConstraint.addConstraint(toAdd);
        }
        return table2constraint;
    }
}