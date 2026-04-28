---
name: agent-commit
description: |
    This agent return commit description evaluating the updates for the provided file path
    required input are:
    * commit file path

tools: diff
---

## Retrieve commit message
To retrieve commit message you must execute the `diff` tool with the given commit file path to get <GIT_DIFF>, analyze it
and produce a structured, technically evaluation to return the git commit message strictly following the rules described
in "Conventional commit rules" paragraph.

The result must following the rules below:
* The identified scope MUST be considered without any path and extension.
* The result MUST be in plain text format avoid markdown format at all.
* The result MUST not be surrounded by quotes or code blocks.
* The result MUST be in English language

### How analyze git diff
The git diff represents changes between two commits.
Lines prefixed with:
+ were added
- were removed
no prefix = context

### Conventional commit rules

The rules are described in file `core/src/test/resources/skills/agent-commit/conventional-commit.md`