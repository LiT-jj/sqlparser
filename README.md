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

### Directory Layout

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

## Design Notes

### High-level Flow

1. Parse SQL into statements via JSqlParser.
2. Walk each statement and build/merge `SQLContext`.
3. Resolve table/column lineage and extract constraints.
4. Return `ParseResult(context, errors)`; optionally throw in strict mode.

### Why Current Architecture

- `JsqlParser` is orchestration-focused (entrypoint + flow control).
- `ParseSession` holds per-parse state to keep parser service stateless across calls.
- `core` helpers isolate responsibilities:
  - `SelectWalker`: join/on traversal details
  - `ColumnResolver`: column key normalization
  - `DerivedAliasRegistry`: alias/derived-source state
  - `ConstraintExtractor`: expression-type decisions

### State & Side-effect Principles

- No cross-request mutable state on `JsqlParser`.
- Parse-scoped mutable data is confined to `ParseSession`.
- Domain models (`SQLContext`, `RefTab`, `RefCol`) are field-encapsulated and mutated via explicit APIs (`add*`, `merge`, `repairColumn`).
- Error handling is explicit in `ParseResult.errors`; strict mode converts errors into exceptions.

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

## API Usage Matrix

All public usage entrypoints:

- `SQLContext getSQLContext(String sql)`
  - Best effort parse
  - Backward-compatible API
  - Ignores parse errors in return type (but logs + stores internally during parse flow)

- `SQLContext getSQLContext(String sql, boolean strict)`
  - `strict=false`: same behavior as `getSQLContext(sql)`
  - `strict=true`: throws `ParseException` when any parse error occurs

- `ParseResult parse(String sql)`
  - Best effort parse
  - Returns both `context` and `errors`

- `ParseResult parse(String sql, boolean strict)`
  - `strict=false`: returns `ParseResult`
  - `strict=true`: throws `ParseException` if `errors` is not empty

### ParseResult / ParseError / ParseException

- `ParseResult`
  - `getContext()`: parsed `SQLContext`
  - `getErrors()`: `List<ParseError>`
  - `hasErrors()`: convenience boolean

- `ParseError`
  - `stage`: where error happened (e.g. `parse-sql`, `convert-statement`)
  - `sql`: original SQL text
  - `statement`: current statement text (if available)
  - `message`: error message
  - `cause`: throwable root cause

- `ParseException`
  - thrown only in strict mode when parse errors exist
  - `getErrors()` returns full error list

### Strict Mode Example

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

