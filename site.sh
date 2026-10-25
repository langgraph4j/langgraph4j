#!/bin/bash

MODE="${1:-run}"
case "$MODE" in
  run|deploy) ;;
  *) echo "Usage: $0 [run|deploy]" >&2; exit 1 ;;
esac

# Build sites and stage results
mvn site:site site:stage -T1 -o
# Build sites and stage results (run locally)
#mvn site:site site:stage -T1 -o -fn

# Run site staging 
# mvn -B site:run -DworkingDirectory=target/staging

cp -r src/site/mkdocs target/mkdocs
cp how-tos/*.ipynb target/mkdocs/how-tos
cp -r target/staging/apidocs target/mkdocs/apidocs

# Copy PostgresQL Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-postgres-saver/ target/mkdocs/core/postgres-saver/

# Copy SQLite Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-sqlite-saver/ target/mkdocs/core/sqlite-saver/

# Copy MySQL Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-mysql-saver/ target/mkdocs/core/mysql-saver/

# Copy Oracle Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-oracle-saver/ target/mkdocs/core/oracle-saver/

# Copy MongoDB Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-mongodb-saver/ target/mkdocs/core/mongodb-saver/

# Copy Redis Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-redis-saver/ target/mkdocs/core/redis-saver/

# Copy DynamoDB Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-dynamodb-saver/ target/mkdocs/core/dynamodb-saver/

# Copy CockroachDB Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-cockroachdb-saver/ target/mkdocs/core/cockroachdb-saver/

# Copy Hazelcast Checkpoint Saver docs
rsync -avm \
--include='*/' \
--include='README.md' \
--include='SAVER*.md' \
--exclude='*' \
langgraph4j-hazelcast-saver/ target/mkdocs/core/hazelcast-saver/

# Copy Spring AI docs
rsync -avm \
  --include='*/' \
  --include='README.md' \
  --exclude='*' \
  spring-ai/ target/mkdocs/integrations/spring-ai/

# Copy LangChain4j docs
rsync -avm \
  --include='*/' \
  --include='README.md' \
  --exclude='*' \
  langchain4j/ target/mkdocs/integrations/langchain4j/

# Copy AG-UI docs
rsync -avm \
  --include='*/' \
  --include='README.md' \
  --exclude='*' \
  ag-ui/ target/mkdocs/ag-ui/

source .docsenv/bin/activate

case "$MODE" in
  run)
    mkdocs build ; mkdocs serve
    ;;
  deploy)
    mike deploy --push --update-aliases 1.9 main
    ;;
esac
