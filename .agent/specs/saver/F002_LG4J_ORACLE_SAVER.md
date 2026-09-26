# F002 Align implementation of Oracle Saver with SQL resource

## Instructions

I want that the Oracle Saver implementation in module [langgraph4j-oracle-saver] will be refactored so that:

* Extract a new class `AbstractOracleSaver` that will be the base class for the Oracle Saver implementation allowing to be extended in the future for other Oracle Saver implementations.
* Remove hardcoded SQL commands and replace them with loading from module resources

To achieve this, take guidance from a reference implementation in module [langgraph4j-mysql-saver].
The steps must be implemented in the following order:
1. Create a new class `AbstractOracleSaver` in module [langgraph4j-oracle-saver] that will be the base class for the Oracle Saver implementation.
2. Refactor the existing Oracle Saver implementation to extend `AbstractOracleSaver`.
3. Remove hardcoded SQL commands from the Oracle Saver implementation and replace them with loading from module resources `db/migration/v1.0__init` and `db/v1.0__commands.sql`.
4. In this phase don't change the existing SQL commands, just move them to the resources and load them from there.

