# sqlparser

一个基于 [JSqlParser](https://github.com/JSQLParser/JSqlParser) 的轻量 SQL 元数据解析器。

可以提取：

- 表（包含子查询/派生表别名）
- 查询字段
- 约束信息（`=`、范围、`IN`、关联关系）

## 功能特性

- 兼容旧入口：
  - `getSQLContext(sql)`
- 新解析 API：
  - `parse(sql)` -> `ParseResult{ context, errors }`
  - `parse(sql, strict)` -> 支持严格模式
- 支持常见 SQL 场景：
  - `WHERE`
  - `JOIN ... ON`
  - `IN (...)` / `IN (subquery)`
  - `UNION` / `SET` 运算
  - 带别名的子查询

## 项目结构

核心包说明：

- `com.jsjjlt.sqlparser`
  - `JsqlParser`：解析编排入口
  - `ParseResult`、`ParseError`、`ParseException`
- `com.jsjjlt.sqlparser.core`
  - `SelectWalker`
  - `ColumnResolver`
  - `DerivedAliasRegistry`
  - `ConstraintExtractor`
- `com.jsjjlt.sqlparser.entity`
  - `SQLContext`、`RefTab`、`RefCol`、`Equal`、`Relate`
- `com.jsjjlt.sqlparser.constraint`
  - `ColumnConstraint`、`TableConstraint`
- `com.jsjjlt.sqlparser.range`
  - 各类范围模型实现

## 快速开始

```java
JsqlParser parser = new JsqlParser();

// 兼容 API（尽力解析）
SQLContext context = parser.getSQLContext("select * from t1 where a = 1");
System.out.println(context);

// 新 API：返回上下文 + 错误信息
ParseResult result = parser.parse("select * from t1 where a = 1");
SQLContext ctx = result.getContext();
if (result.hasErrors()) {
    result.getErrors().forEach(System.out::println);
}

// 严格模式：只要有错误就抛 ParseException
SQLContext strictCtx = parser.getSQLContext("select * from t1", true);
```

## 输出模型说明

`SQLContext` 主要包含：

- `tables`：当前语句识别出的表
- `allTables`：合并后的全表视图（含子查询上下文）
- `columns`：引用/输出字段
- `column2constraint`：字段到约束的映射

`ColumnConstraint` 主要数据：

- `equals`：等值条件
- `ranges`：范围条件
- `relates`：字段关联关系（如 join 条件）

## 构建与测试

```bash
# 编译
mvn -DskipTests compile

# 执行测试
mvn test
```

## 测试覆盖

当前单元测试覆盖：

- where
- join
- in
- union
- subquery
- 回归场景：union + alias + constant
- strict 模式异常行为

测试文件：

- `src/test/java/com/jsjjlt/sqlparser/JsqlParserTest.java`

