---
name: docs-springai
description: Refine, align, and maintain technical documentation for the LangGraph4j Spring AI modules spring-ai/spring-ai-core and spring-ai/spring-ai-agent, including their README files, Maven site markdown, examples, cross-links, and the summary entry point in spring-ai/README.md.
---

# Spring AI Documentation Alignment

Use this skill when asked to improve, rewrite, audit, align, or extend documentation for:

- `spring-ai/spring-ai-core`
- `spring-ai/spring-ai-agent`
- the aggregate entry point `spring-ai/README.md`

The goal is to keep the Spring AI documentation accurate, coherent, navigable, and grounded in the current Java code and Maven metadata.

## Documentation Surfaces

Primary files:

- `spring-ai/README.md`: top-level summary and access point for Spring AI integration docs.
- `spring-ai/spring-ai-core/README.md`: user-facing overview for core Spring AI utilities.
- `spring-ai/spring-ai-agent/README.md`: user-facing overview for the ReAct agent executor.

Site documentation:

- `spring-ai/spring-ai-core/src/site/markdown/index.md`
- `spring-ai/spring-ai-core/src/site/markdown/00_index.md`
- `spring-ai/spring-ai-core/src/site/markdown/*.md`
- `spring-ai/spring-ai-agent/src/site/markdown/index.md`

Source-of-truth code and metadata:

- `spring-ai/spring-ai-core/pom.xml`
- `spring-ai/spring-ai-agent/pom.xml`
- `spring-ai/spring-ai-core/src/main/java`
- `spring-ai/spring-ai-agent/src/main/java`
- focused tests and examples under each module's `src/test/java` and `src/test/resources`

## Workflow

1. Inspect the existing docs before editing.
   - Read the relevant README and Maven site markdown.
   - Read the matching `pom.xml` for artifact ids, dependency names, Java level, and module purpose.
   - Search the module source for the documented APIs, builders, methods, and package names.

2. Verify examples against code.
   - Confirm class names, package names, builder methods, state keys, and imports exist.
   - Prefer examples copied or adapted from tests when they are current.
   - Do not document APIs from old examples if the current source no longer supports them.

3. Align the module narrative.
   - `spring-ai-core` should describe reusable Spring AI integration utilities: streaming chat generation, tool service support, state serialization, and message serialization.
   - `spring-ai-agent` should describe the Spring AI ReAct agent executor built on LangGraph4j, including graph construction, tool callbacks, chat model configuration, execution flow, and optional LangGraph Studio integration.
   - Keep module names and Maven artifact ids exact:
     - `langgraph4j-spring-ai`
     - `langgraph4j-springai-agentexecutor`

4. Update `spring-ai/README.md` whenever module docs change.
   - Provide a short project-level summary.
   - Add direct links to `spring-ai-core` and `spring-ai-agent` documentation.
   - Mention each module's purpose in one or two sentences.
   - Keep it as a navigation hub, not a duplicated full manual.

5. Keep README and site docs consistent.
   - README files should be concise, practical, and immediately useful from GitHub.
   - Site markdown can include deeper tutorial structure, diagrams, and generated reference content.
   - If a concept appears in both places, names, examples, and links must agree.

6. Validate the final docs.
   - Check Markdown headings, links, fenced code blocks, and relative paths.
   - Search for stale terms such as old artifact ids, obsolete builder method names, broken image paths, or generated placeholder links like `[None](None)`.
   - Run documentation-related Maven commands only if the task requires generated site validation.

## Writing Standards

- Use clear technical prose aimed at Java developers adopting LangGraph4j with Spring AI.
- Start each module README with what the module does, when to use it, and the Maven dependency.
- Prefer short sections with concrete examples over broad marketing language.
- Keep code blocks compilable in spirit: include enough context to explain usage without turning docs into full applications.
- Use consistent spelling:
  - `LangGraph4j`
  - `Spring AI`
  - `ReAct`
  - `ChatModel`
  - `ChatClient`
  - `ToolCallback`
- Avoid emojis in new prose unless preserving an existing heading style is explicitly desired.
- Do not invent features, configuration flags, or dependency coordinates.

## Recommended README Shape

For `spring-ai/README.md`:

```markdown
# LangGraph4j Spring AI Integration

Short summary.

## Modules

- [spring-ai-core](spring-ai-core/README.md): ...
- [spring-ai-agent](spring-ai-agent/README.md): ...

## Documentation

- [Core Maven site docs](spring-ai-core/src/site/markdown/index.md)
- [Agent Maven site docs](spring-ai-agent/src/site/markdown/index.md)
```

For module READMEs:

```markdown
# Module Title

Short purpose statement.

## Features

## Installation

## Usage

## Related Documentation
```

## Common Fixes

- Replace stale dependency snippets with coordinates from the module `pom.xml`.
- Replace broken or generated placeholder links with real repository-relative links.
- Move duplicated long explanations from `spring-ai/README.md` into module docs and link to them.
- Prefer diagrams already present in `src/site/resources` or Mermaid blocks already maintained in the docs.
- Preserve generated tutorial files when they are still useful, but clearly separate generated reference content from hand-written getting-started guidance.

## Done Criteria

A documentation update is complete when:

- `spring-ai/README.md` gives a useful summary and direct access to both module docs.
- The changed module docs match current Java APIs and Maven coordinates.
- Cross-links resolve relative to their Markdown file locations.
- Examples use current Spring AI and LangGraph4j names.
- There are no obvious stale placeholders, contradictory descriptions, or unsupported claims.
