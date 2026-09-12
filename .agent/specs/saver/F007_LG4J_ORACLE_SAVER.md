# F007 - Align implementation of Oracle Saver with the Sqlite Saver one

## Description

I want align the implementation of Oracle Saver in module [langgraph4j-oracle-saver] with the Sqlite Saver one in module [langgraph4j-sqlite-saver]

## Instructions

- Covert SQLite DDL commands in langgraph4j-sqlite-saver/src/main/resources/db/v2.0__init.sql to equivalent Oracle DDL commands
  in file langgraph4j-oracle-saver/src/main/resources/db/v2.0__init.sql
- Accordingly with new schema, convert SQLite SQL commands in langgraph4j-sqlite-saver/src/main/resources/db/v2.0__commands.sql to equivalent Oracle SQL commands
  in file langgraph4j-oracle-saver/src/main/resources/db/v2.0__commands.sql

Note: keep all comments in the SQL files as it is, and only change the SQL commands to be compatible with Oracle syntax.



