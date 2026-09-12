# F005 - Align implementation of MySQL Saver with the SQLite Saver one

## Description

I want align the implementation of MySQL Saver in module [langgraph4j-mysql-saver] with the SQLite Saver one in module [langgraph4j-sqlite-saver]

## Instructions

- Covert SQLite DDL commands in langgraph4j-sqlite-saver/src/main/resources/db/v2.0__init.sql to equivalent MySQL DDL commands
  in file langgraph4j-mysql-saver/src/main/resources/db/v2.0__init.sql
- Accordingly with new schema, convert SQLite SQL commands in langgraph4j-sqlite-saver/src/main/resources/db/v2.0__commands.sql to equivalent MySQL SQL commands
  in file langgraph4j-mysql-saver/src/main/resources/db/v2.0__commands.sql

Note: keep all comments in the SQL files as it is, and only change the SQL commands to be compatible with MySQL syntax.


