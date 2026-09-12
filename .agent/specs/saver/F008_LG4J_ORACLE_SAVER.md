# F008 - Align documentation of Oracle Saver from the SQLite Saver one

## Description

I want align the documentation of Oracle Saver in module [langgraph4j-oracle-saver] with the SQLite Saver one in module [langgraph4j-sqlite-saver]
Concerning [langgraph4j-oracle-saver] module update current `README.md` and add new `SAVER_V1.md` and `SAVER_V2.md` files
Add a new item in [mkdocs.yml] under `Checkpoint saver:` menu
Add a new copy command in [site-run.sh] to copy documentation files from [langgraph4j-oracle-saver] module to `target/mkdocs/core/*` folder
add a new copy command in [.github/workflows/deploy-site.yml] to copy documentation files from [langgraph4j-oracle-saver] module to `target/mkdocs/core/*` folder








