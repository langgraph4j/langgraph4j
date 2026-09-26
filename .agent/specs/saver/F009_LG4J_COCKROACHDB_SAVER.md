# F009 Align implementation of CockroachDB Saver with SQL resource

## Instructions

I want that the CockroachDB Saver implementation in module [langgraph4j-cockroachdb-saver] will be refactored so that:

* Extract a new class `AbstractCockroachDBSaver` that will be the base class for the CockroachDB Saver implementation allowing to be extended in the future for other CockroachDB Saver implementations.
* Remove hardcoded SQL commands and replace them with loading from module resources

To achieve this, take guidance from a reference implementation in module [langgraph4j-postgres-saver].
The steps must be implemented in the following order:
1. Create a new class `AbstractCockroachDBSaver` in module [langgraph4j-cockroachdb-saver] that will be the base class for the CockroachDB Saver implementation.
2. Refactor the existing CockroachDB Saver implementation to extend `AbstractCockroachDBSaver`.
3. Remove hardcoded SQL commands from the CockroachDB Saver implementation and replace them with loading from module resources `db/migration/v1.0__init` and `db/v1.0__commands.sql`.
4. In this phase don't change the existing SQL commands, just move them to the resources and load them from there.


