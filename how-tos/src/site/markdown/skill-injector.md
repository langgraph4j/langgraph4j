# SkillInjector (dynamic skills via tool success)

Tool-call–triggered skills for LangChain4j `AgentExecutor`: activate **skill ids** in Graph State after successful tool results; inject skill **bodies** into the next model request via `ConversationContextPolicy` (bodies never enter Graph State).

## Setup

```java
var injector = SkillInjector.builder()
    .resolver(new MapToolSkillResolver()
        .bind("query_logistics", "order-reply"))
    // pick one body source:
    .skillsFromClassPath("skills")          // classpath:skills/.../SKILL.md
    // .skills(ClassPathSkillLoader.loadSkills("skills"))
    // .skillsFromPath(Path.of("my-skills"))
    // .skillBody(id -> "...")               // custom
    .build();

var graph = AgentExecutor.builder()
    .chatModel(model)
    .toolsFromObject(businessTools)
    .skillInjector(injector)   // executeTools hook + conversation policy
    .build();
```

Static build-time `.skills(...)` on the builder still works for catalog listing in the system prompt; `SkillInjector` is for **runtime activation** after tools succeed.

## Behaviour

| Step | What happens |
|------|----------------|
| Tool succeeds (`ToolExecutionResultMessage` and `isError != true`) | Resolver maps tool → skill ids → merge into `active_skills` via `Command.update` |
| Tool fails / missing result | **No** activation |
| Next `CallModel` | Policy prepends skill bodies to the request message view |
| Graph State `messages` | Unchanged by injection (no skill body persisted) |

## Lifetime

`active_skills` follows Graph State / checkpoint / `threadId`. Same thread without `releaseThread` ≈ Sticky; new thread or `CompileConfig.releaseThread(true)` ≈ Ephemeral.

`AgentExecutor.State.SCHEMA` always includes the optional `active_skills` channel; without `skillInjector`, nothing is activated and ReAct behaviour is unchanged.

## Hooks only

```java
AgentExecutor.builder()
    .addCallModelHook(...)
    .addExecuteToolsHook(injector.executeToolsHook())
    .conversationContextPolicy(injector.asConversationContextPolicy(existingPolicy))
    ...
```
