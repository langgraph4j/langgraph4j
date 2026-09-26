# F0044 Align implementation of Postgres Saver with the SQLite Saver one

## Description

I want align the implementation of Postgres Saver in module [langgraph4j-postgres-saver] with the SQLite Saver one in module [langgraph4j-sqlite-saver]

## Instructions

- I want add the sql commands `sqlSelectTag`, `sqlSelectAllThreads`, `sqlSelectAllTags` to PostgresSaver module in
file [langgraph4j-postgres-saver/src/main/resources/db/v2.0__commands.sql] converting the same commands from SQLite module
in file [langgraph4j-sqlite-saver/src/main/resources/db/v2.0__commands.sql]

- I want add a new  `AbstractPostgresSaverV2` class converting  the `AbstractSQLiteSaverV2 one, after that
refactor the `PostgresSaverV2` class to extend the `AbstractPostgresSaverV2` class.

- I want add a new `AbstractPostgresSaverV2Dashboard` class converting  the `AbstractSQLiteSaverV2Dashboard` one

- I want that you add in the test module the `JtPostgresSaverDashboardApp` class converting the `JtSQLiteSaverDashboardApp` one


