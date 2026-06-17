# Conventional Commit guidelines

Commits MUST be formatted as follows:

```
<type>[optional scope]: <description>
[optional body]
[optional footer(s)]
```

- <type> could be one of the following:
    * 'feat' MUST be used when a commit adds a new feature to your application or library.
    * 'build' MUST be used when changes are made to the project configuration files, scripts, affect the build system or external dependencies.
    * 'refactor' MUST be used when code changes neither fix bugs nor add features.
    * 'docs' MUST be used when changes are related to documentation.
    * 'test' MUST be used when adding missing tests or correcting existing tests.
    * 'fix' MUST be used when a commit represents a bug fix for your application.
    * 'style' MUST be used when changes  don't affect code meaning (formatting, spacing).
    * 'perf' MUST be used when changes improve performance.
    * 'ci' MUST be used when changes affect the Continuous Integration configuration files and scripts.
    * 'revert' MUST be used when reverting changes.
- A <scope> MAY be provided after a type.
    A scope MUST consist of a noun describing a section of the codebase surrounded by parenthesis, e.g., fix(parser):.
    If one file is affected by the commit, the filename is used as the scope.
- A <description> MUST immediately follow the colon and space after the type/scope prefix. The description is a short summary of the code changes, e.g., fix: array parsing issue when multiple spaces were contained in string.
- A <body> MAY be provided for longer commit after the short description, providing additional contextual information about the code changes. The body MUST begin one blank line after the description.
  A commit body is free-form and MAY consist of any number of newline separated paragraphs.
