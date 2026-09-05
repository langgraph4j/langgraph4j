# Prompt chaining

Original tutorial: [Prompt chaining](https://docs.langchain.com/oss/python/langgraph/workflows-agents#prompt-chaining).

Prompt chaining runs a sequence of LLM calls where each step uses the result of the previous step. This workflow generates a joke, checks whether it has a punchline, and only improves it when needed.

## Setup

Set `OPENAI_API_KEY` before running the model calls.

```java
var userHomeDir = System.getProperty("user.home");
var localRepoUrl = "file://" + userHomeDir + "/.m2/repository/";
var langchain4jVersion = "1.19.0";
var langgraph4jVersion = "1.8.27";
```

```java
%dependency /add-repo local \{localRepoUrl} release|never snapshot|always
%dependency /add org.bsc.langgraph4j:langgraph4j-core:\{langgraph4jVersion}
%dependency /add dev.langchain4j:langchain4j-open-ai:\{langchain4jVersion}
%dependency /resolve
```

```java
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Objects;

var apiKey = Objects.requireNonNull(
        System.getenv("OPENAI_API_KEY"),
        "Set OPENAI_API_KEY before running this notebook."
);

var model = OpenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName("gpt-4o-mini")
        .temperature(0.0)
        .build();
```

## Define the state

The state keeps the topic and each version of the joke. Each node updates only the field it produces.

```java
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.Map;

class JokeState extends AgentState {
    static final String TOPIC = "topic";
    static final String JOKE = "joke";
    static final String IMPROVED_JOKE = "improved_joke";
    static final String FINAL_JOKE = "final_joke";

    static final Map<String, Channel<?>> SCHEMA = Map.of(
            TOPIC, Channels.base(() -> ""),
            JOKE, Channels.base(() -> ""),
            IMPROVED_JOKE, Channels.base(() -> ""),
            FINAL_JOKE, Channels.base(() -> "")
    );

    JokeState(Map<String, Object> initData) {
        super(initData);
    }

    String topic() {
        return this.<String>value(TOPIC).orElse("");
    }

    String joke() {
        return this.<String>value(JOKE).orElse("");
    }

    String improvedJoke() {
        return this.<String>value(IMPROVED_JOKE).orElse("");
    }

    String finalJoke() {
        return this.<String>value(FINAL_JOKE).orElse("");
    }
}
```

## Define the nodes and routing

```java
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;

NodeAction<JokeState> generateJoke = state -> {
    var response = model.chat(UserMessage.from(
            "Write a short joke about " + state.topic()
    ));
    return Map.of(JokeState.JOKE, response.aiMessage().text());
};

EdgeAction<JokeState> checkPunchline = state ->
        state.joke().contains("?") || state.joke().contains("!") ? "Pass" : "Fail";

NodeAction<JokeState> improveJoke = state -> {
    var response = model.chat(UserMessage.from(
            "Make this joke funnier by adding wordplay: " + state.joke()
    ));
    return Map.of(JokeState.IMPROVED_JOKE, response.aiMessage().text());
};

NodeAction<JokeState> polishJoke = state -> {
    var response = model.chat(UserMessage.from(
            "Add a surprising twist to this joke: " + state.improvedJoke()
    ));
    return Map.of(JokeState.FINAL_JOKE, response.aiMessage().text());
};
```

## Build and run the workflow

```java
import org.bsc.langgraph4j.StateGraph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

var workflow = new StateGraph<>(JokeState.SCHEMA, JokeState::new)
        .addNode("generate_joke", node_async(generateJoke))
        .addNode("improve_joke", node_async(improveJoke))
        .addNode("polish_joke", node_async(polishJoke))
        .addEdge(START, "generate_joke")
        .addConditionalEdges("generate_joke", edge_async(checkPunchline), Map.of(
                "Pass", END,
                "Fail", "improve_joke"
        ))
        .addEdge("improve_joke", "polish_joke")
        .addEdge("polish_joke", END);

var chain = workflow.compile();

var result = chain.invoke(Map.of(JokeState.TOPIC, "cats"))
        .orElseThrow();

System.out.println("Initial joke:\n" + result.joke());
if (result.improvedJoke().isBlank()) {
    System.out.println("\nFinal joke:\n" + result.joke());
} else {
    System.out.println("\nImproved joke:\n" + result.improvedJoke());
    System.out.println("\nFinal joke:\n" + result.finalJoke());
}
```
