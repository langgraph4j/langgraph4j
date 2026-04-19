# Move To Release 1.9 (draft)

## Executive Summary

The main runtime changes in this patch set are:

- `CompileConfig.releaseThread` now defaults to `true`.
- Subgraph resume metadata is now cleaned up automatically after node execution.
- `RunnableConfig` metadata is now mutable during execution.
- `GsonStateSerializer` is deprecated for removal.
- Spring AI model invocation now fails fast when no output is returned.

## Main Migration Themes

### 1. Thread release behavior changed by default

- Previous behavior: checkpoint-backed executions kept thread state unless `releaseThread(true)` was explicitly set.
- New behavior: `releaseThread` defaults to `true`.
- Potential breaking change: flows that expect checkpoints to remain available after completion or interruption may stop finding persisted state.
- Migration suggestion: explicitly set `.releaseThread(false)` in `CompileConfig` anywhere you rely on later resume, replay, inspection, or manual checkpoint lifecycle management.

### 2. Saver Tag

- `BaseCheckpointSaver.Tag` now also carries a `version`.


### 4. Treat `RunnableConfig` as execution-scoped

- Metadata is no longer copied into an immutable map at construction time.
- Metadata could mutate during workflow execution. Mutation is allowed only by internal stuff.
- Potential breaking change: code that assumes `RunnableConfig` metadata is immutable may observe different behavior.

