# 🦜🕸️ Welcome to LangGraph4j

### *Enabling Graph Engineering for Agentic AI Workflows in Java*

----

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg) [![docs](https://img.shields.io/badge/Site-Documentation-blue)][documentation] [![Static Badge](https://img.shields.io/badge/maven--snapshots-1.9--SNAPSHOT-blue)][snapshots] [![Maven Central](https://img.shields.io/maven-central/v/org.bsc.langgraph4j/langgraph4j-core.svg)][releases][![discord](https://img.shields.io/discord/1364514593765986365?logo=discord&style=flat)](https://discord.gg/szVVztSYKh)

<u>LangGraph for Java. A library for building stateful, multi-agents applications with LLMs</u>, built for work with [Langchain4j] and [Spring AI]
> It is inspired by [LangGraph] solution, part of [LangChain AI project][langchain.ai].

LangGraph4j is a flexible, powerful orchestration framework built for Java developers working with LangChain4j. It helps you build intelligent applications using AI workflows, graphs, and multi-agent systems with ease.

## Features

At macro level these are the features that support the framework:

- **StateGraph**: Nodes, Edges (conditional and normal), Entry Points, State Schema

- **Async & Streaming support** (via `CompletableFuture`, Java-async-generator)

- **Checkpoints** (save, replay), Breakpoints (pause/resume)

- **Visualization, Interaction, and Debugging using** [Studio] Web App **integrated with:** [Spring Boot], [Quarkus] & [Jetty]

- **Graph visualization** (PlantUML, Mermaid)

- **Multi-agent support**, threads, sub-graphs, and parallel node execution

- **Integrations**: [Langchain4j], [Spring AI], 

- **Visual builder** ([LangGraph4j Builder])


<u>LangGraph4j is a complete solution for building LLM workflows in Java</u>:

- Visual + Code = Clarity

- Async + Graph = Scalability

- Checkpoints + Studio = Debug-friendly

- Agents + Integration = Power

For more details please see [Features](features.md) 

<!--
* [LangGraph - LangChain Blog][langgraph.blog]
-->

[Jetty]: https://jetty.org
[Spring Boot]: https://spring.io/projects/spring-boot
[Quarkus]: https://quarkus.io
[Spring AI]: https://spring.io/projects/spring-ai

[documentation]: https://langgraph4j.github.io/langgraph4j/
[javadocs]: /langgraph4j/apidocs/
[springai-agentexecutor]: spring-ai/spring-ai-agent
[agent-executor]: langchain4j/langchain4j-agent


[Studio]: studio/
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
[article01]: https://bsorrentino.github.io/bsorrentino/ai/2024/05/20/langgraph-for-java.html
[langgraph.blog]: https://blog.langchain.dev/langgraph/
[Langchain4j]: https://github.com/langchain4j/langchain4j
[langchain.ai]: https://github.com/langchain-ai
[langchain]: https://github.com/langchain-ai/langchain/
[langgraph]: https://github.com/langchain-ai/langgraph
[langchain.agents]: https://python.langchain.com/docs/modules/agents/
[AgentExecutor]: https://github.com/langchain-ai/langchain/blob/master/libs/langchain/langchain/agents/agent.py
[PlantUML]: https://plantuml.com
[java-async-generator]: https://github.com/bsorrentino/java-async-generator
[Mermaid]: https://mermaid.js.org

[releases]: https://central.sonatype.com/search?q=a%3Alanggraph4j-parent
[snapshots]: https://central.sonatype.com/repository/maven-snapshots/org/bsc/langgraph4j/

[LangGraph4j Builder]: https://github.com/langgraph4j/langgraph4j-builder
[generator]: https://github.com/bsorrentino/langgraph4j/tree/main/generator