# F001 add langgraph4j-sqlite-saver module

## Instructions

We must add a new maven module `langgraph4j-sqlite-saver` to implement support of SQLite as a CheckpointSaver.

You must implement a new class `SQLiteSaver` that extends the @langgraph4j-core/src/main/java/org/bsc/langgraph4j/checkpoint/AbstractCheckpointSaver.java class and save the checkpoints in a SQLite database.

Take inspiration from the existing @langgraph4j-postgres-saver module and implement the same methods to save and load checkpoints in a SQLite database.

As tech. stack use the [SQLite JDBC Driver](https://github.com/xerial/sqlite-jdbc)

update README.md "Project Structure" paragraph accordingly