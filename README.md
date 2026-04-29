# sqlparser

A lightweight SQL metadata parser based on [JSqlParser](https://github.com/JSQLParser/JSqlParser).

It extracts:

- tables (including subquery/derived aliases)
- selected columns
- constraints (`=`, range, `IN`, join relations)

## Features

- Parse entrypoint with backward compatibility:
  - `getSQLContext(sql)`
- Rich parse API:
  - `parse(sql)` -> `ParseResult{ context, errors }`
  - `parse(sql, strict)` -> strict mode support
- Handles common query patterns:
  - `WHERE`
  - `JOIN ... ON`
  - `IN (...)` / `IN (subquery)`
  - `UNION` / `SET` operations
  - subquery with aliases

## Project Structure

Core packages:

- `com.jsjjlt.sqlparser`
  - `JsqlParser`: orchestration entrypoint
  - `ParseResult`, `ParseError`, `ParseException`
- `com.jsjjlt.sqlparser.core`
  - `SelectWalker`
  - `ColumnResolver`
  - `DerivedAliasRegistry`
  - `ConstraintExtractor`
- `com.jsjjlt.sqlparser.entity`
  - `SQLContext`, `RefTab`, `RefCol`, `Equal`, `Relate`
- `com.jsjjlt.sqlparser.constraint`
  - `ColumnConstraint`, `TableConstraint`
- `com.jsjjlt.sqlparser.range`
  - range model implementations

## Quick Start

```java
JsqlParser parser = new JsqlParser();

// Compatibility API (best effort parse)
SQLContext context = parser.getSQLContext("select * from t1 where a = 1");
System.out.println(context);

// Rich API with parse diagnostics
ParseResult result = parser.parse("select * from t1 where a = 1");
SQLContext ctx = result.getContext();
if (result.hasErrors()) {
    result.getErrors().forEach(System.out::println);
}

// Strict mode (throw ParseException when any parse error occurs)
SQLContext strictCtx = parser.getSQLContext("select * from t1", true);
```

## Output Model

`SQLContext` contains:

- `tables`: recognized tables in current statement
- `allTables`: merged table view (including nested/subquery contexts)
- `columns`: referenced/selected columns
- `column2constraint`: column -> extracted constraints

Typical `ColumnConstraint` data:

- `equals`: equality values
- `ranges`: boundary constraints
- `relates`: relational constraints to other columns (e.g. join)

## Build & Test

```bash
# compile
mvn -DskipTests compile

# run tests
mvn test
```

## Test Coverage

Current unit tests include:

- where
- join
- in
- union
- subquery
- regression case: union + alias + constant
- strict mode exception behavior

Test file:

- `src/test/java/com/jsjjlt/sqlparser/JsqlParserTest.java`

