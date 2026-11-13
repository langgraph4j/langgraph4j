---
name: alm-processing
description: Use this to perform operations on the langgraph4j project. 1) create a new project release 2) move to next dev release
---

# create a new project release

This project is on a support branch `support/1.8.x`
To create a new release we must accomplish the workflow outlined below:

## 1. Update project release
Update the java project release runs the command `mvn versions:set -DnewVersion=<version>`.
After the command successfully completed we must to confirm changes in `pom.xml` files runs command `mvn version:commit`

## 2. commit the release update
After update java project release we must commit to git runs commands
```
git add . ; git commit -m'build: bump to next version <version>'
```
## 2.1 rename old release reference
rename all previous release references from project files (*.md, *.ipynb, *.java) that contains it and commit the changes to git runs commands
```
git add . ; git commit -m'chore: rename old release reference to <version>'
```

## 3. update change log
To update change log follow the steps below :

### 3.1 generate CHANGELOG.md
exec command `git-changelog-command-line -of CHANGELOG.md`

### 3.2 commit changes
exec command `git commit -m'docs: update changelog' -a`

## 4. close release
To close the release runs `git tag -a v<version> -m"new release <version>"` that will create a new tag for the release.




