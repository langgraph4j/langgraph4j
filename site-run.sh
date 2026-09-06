#!/bin/bash

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

source .docsenv/bin/activate

mkdocs build

mkdocs serve 

# Site deploy with version
# mike deploy --push --update-aliases 1.9 main