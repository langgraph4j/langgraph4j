# Spring AI Agent Archetype

This Maven archetype creates a new Spring Boot project configured to work with LangGraph4j, providing a starting point for building AI agents powered by Spring AI.

## Usage

To generate a new project from this archetype, run the following Maven command in your terminal.

### Non-Interactive Mode

This command generates the project with predefined properties.

```bash
mvn archetype:generate \
  -DarchetypeGroupId=org.bsc.langgraph4j \
  -DarchetypeArtifactId=spring-ai-agent-archetype \
  -DarchetypeVersion=1.8.17
```

**Parameters:**

*   `archetypeGroupId`: The group ID of the archetype (`org.bsc.langgraph4j`).
*   `archetypeArtifactId`: The artifact ID of the archetype (`spring-ai-agent-archetype`).
*   `archetypeVersion`: The version of the archetype.
*   `groupId`: The group ID for your new project (e.g., `com.example`).
*   `artifactId`: The artifact ID for your new project (e.g., `my-spring-ai-agent`).
*   `version`: The version for your new project (e.g., `1.0.0`).
*   `package`: The package name for your new project (e.g., `com.example.mypackage`).`

### Interactive Mode

Alternatively, you can run the command in interactive mode and provide the values when prompted.

```bash
mvn archetype:generate \
  -DarchetypeGroupId=org.bsc.langgraph4j \
  -DarchetypeArtifactId=spring-ai-agent-archetype \
  -DarchetypeVersion=1.8.17
```

Maven will then prompt you to enter the `groupId`, `artifactId`, `version`, and `package` for your new project.

## Generated Project

After running the command, a new directory with the name of your `artifactId` (e.g., `my-spring-ai-agent`) will be created. This project contains a sample Spring Boot application demonstrating how to create a ReAct-style agent using LangGraph4j and Spring AI, complete with example tools and configurations for different AI models.

```
