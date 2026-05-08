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

### 目录结构

```text
sqlparser/
├─ pom.xml
├─ README.md
├─ README.zh-CN.md
└─ src/
   ├─ main/
   │  └─ java/
   │     ├─ com/jsjjlt/sqlparser/
   │     │  ├─ JsqlParser.java
   │     │  ├─ ParseResult.java
   │     │  ├─ ParseError.java
   │     │  ├─ ParseException.java
   │     │  ├─ core/
   │     │  │  ├─ SelectWalker.java
   │     │  │  ├─ ColumnResolver.java
   │     │  │  ├─ DerivedAliasRegistry.java
   │     │  │  └─ ConstraintExtractor.java
   │     │  ├─ entity/
   │     │  ├─ constraint/
   │     │  ├─ range/
   │     │  └─ utils/
   │     └─ Test.java
   └─ test/
      └─ java/
         └─ com/jsjjlt/sqlparser/
            └─ JsqlParserTest.java
```

## 设计思路

### 高层流程

1. 使用 JSqlParser 将 SQL 拆分为 statements。
2. 逐条遍历 statement，构建并合并 `SQLContext`。
3. 完成表/字段归属解析与约束提取。
4. 返回 `ParseResult(context, errors)`；strict 模式下有错即抛异常。

### 架构设计原因

- `JsqlParser` 负责编排流程（入口 + 过程控制）。
- `ParseSession` 持有“单次解析”状态，保证解析器服务在多次调用间无共享可变状态。
- `core` 下按职责拆分：
  - `SelectWalker`：join/on 遍历细节
  - `ColumnResolver`：字段 key 归一化
  - `DerivedAliasRegistry`：派生别名与来源状态管理
  - `ConstraintExtractor`：表达式类型判断

### 状态与副作用原则

- `JsqlParser` 不保留跨请求可变状态。
- 解析过程中的可变状态限制在 `ParseSession`。
- 领域对象（`SQLContext`、`RefTab`、`RefCol`）采用字段封装，并通过显式入口（`add*`、`merge`、`repairColumn`）变更。
- 错误通过 `ParseResult.errors` 显式暴露；strict 模式将错误提升为异常。

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

## API 用法总览

当前所有对外公开入口如下：

- `SQLContext getSQLContext(String sql)`
  - 尽力解析（best effort）
  - 兼容旧调用方式
  - 返回值仅包含 `SQLContext`

- `SQLContext getSQLContext(String sql, boolean strict)`
  - `strict=false`：行为与 `getSQLContext(sql)` 一致
  - `strict=true`：只要出现解析错误就抛 `ParseException`

- `ParseResult parse(String sql)`
  - 尽力解析
  - 返回 `context + errors`

- `ParseResult parse(String sql, boolean strict)`
  - `strict=false`：返回 `ParseResult`
  - `strict=true`：若存在错误，抛 `ParseException`

### ParseResult / ParseError / ParseException

- `ParseResult`
  - `getContext()`：解析后的 `SQLContext`
  - `getErrors()`：`List<ParseError>`
  - `hasErrors()`：是否存在错误

- `ParseError`
  - `stage`：错误阶段（如 `parse-sql`、`convert-statement`）
  - `sql`：原始 SQL
  - `statement`：当前子语句（如果可用）
  - `message`：错误信息
  - `cause`：原始异常

- `ParseException`
  - 仅在 strict 模式且存在错误时抛出
  - 可通过 `getErrors()` 拿到完整错误列表

### Strict 模式示例

```java
JsqlParser parser = new JsqlParser();
try {
    SQLContext context = parser.getSQLContext("select * from t=4", true);
    System.out.println(context);
} catch (ParseException e) {
    e.getErrors().forEach(err ->
            System.err.println(err.getStage() + " -> " + err.getMessage()));
}
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

