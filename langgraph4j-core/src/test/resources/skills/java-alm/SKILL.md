---
name: java-alm
description: Use this to perform creating new project releases on the maven based java project. 
---

# create a new project release

In this project we follow the "git flow" branching strategy. 
To create a new release we need to start from `develop` branch and accomplish the workflow outlined below:

## 1. Create a ad-hoc release branch.
Starting from `develop` branch and runs the command `git flow release start "<version>"` that will create a branch from `develop` named `release\<version>`.

## 2. Update project release
After created successfully the release branch we must update the java project release runs the command `mvn versions:set -DnewVersion=<version>`. 
After the command successfully completed we must to confirm changes in `pom.xml` files runs command `mvn version:commit`

## 3. commit the release update
After update java project release we must commit to git runs commands
```
git add . ; git commit -m'build: bump to next version <version>'
```
## 4. wait for confirmation
At this point pause execution and ask to the user when ready to close the release, When he confirms proceed to next steps

## 5. close release
To close the release runs `git flow release finish "<version>" -m"new release <version>"` that will merge the branch `release/<version>` to both `main` and `develop` ones 

## 6. update change log
To update change log follow the steps below :
### 6.1 create an hotfix branch to work on changelog
exec command `git flow hotfix start changelog`

### 6.2 generate CHANGELOG.md
exec command `git-changelog-command-line -of CHANGELOG.md`

### 6.3 commit changes
exec command `git commit -m'docs: update changelog' -a`

### 6.4 finish the hotfix without create tag
exec command `git flow hotfix finish changelog -n -m"changelog hotfix merge"`


# Create a new dev release (i.e. SNAPSHOT)

The dev version must have the postfix `-SNAPSHOT` and we need to start from `develop` branch. 

To create a new dev release we need to follow the steps below:

## 1. Update project release
runs the command `mvn versions:set -DnewVersion=<dev version>`. 
After the command successfully completed we must to confirm changes in `pom.xml` files runs command `mvn version:commit`

## 2. commit the release update
After update java project release we must commit to git runs commands `git commit -m'build: bump to next dev version <dev version>' -a`

