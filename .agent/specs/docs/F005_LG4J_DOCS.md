# F005 - Update PostgreSQL Checkpoint Saver Documentation

## Instructions

Write a technical documentation related to 'PostgreSQL Checkpoint Saver' in module [langgraph4j-postgres-saver].
The documentation must be organized in the following files:

1. `README.md`: This file should provide a comprehensive overview of the PostgreSQL Checkpoint Saver, including its purpose, features, and benefits.
   It should also include instructions on how to set up and configure the saver, as well as any prerequisites or dependencies.
   It must refers to two different implementations `version 1` and `version 2` point to file [SAVER_V1.md](./SAVER_V1.md) and [SAVER_V2.md](./SAVER_V2.md) respectively..
2. `SAVER_V1.md`: This file should provide detailed information about the first implementation of the PostgreSQL Checkpoint Saver relate to the class `PostgresSaver`. It must include:
    * Data architecture in file [v1.0__init.sql](./db/migration/v1.0__init.sql) as mermaid chart
    * Design decisions, and any specific features or limitations.
    * Sample code snippets demonstrating how to build a new saver instance.
3. `SAVER_V2.md`: This file should provide detailed information about the second implementation of the PostgreSQL Checkpoint Saver relate to the class `PostgresSaver`. It must include:
    * Data architecture in file [v2.0__init.sql](./db/migration/v2.0__init.sql) as mermaid chart
    * Design decisions, and any specific features or limitations.
    * Sample code snippets demonstrating how to build a new saver instance.
