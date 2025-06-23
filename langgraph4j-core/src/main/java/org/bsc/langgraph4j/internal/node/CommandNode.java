package org.bsc.langgraph4j.internal.node;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;

public class CommandNode<State extends AgentState> extends Node<State> {

  private final Map<String, String> mappings;
  private final AsyncCommandAction<State> action;

  public CommandNode(String id, AsyncCommandAction<State> action, Map<String, String> mappings) {
    super(id, (config) -> new AsyncCommandNodeActionWithConfig<>(action, mappings));
    this.mappings = mappings;
    this.action = action;
  }

  public AsyncCommandAction<State> getAction() {
    return action;
  }

  public Map<String, String> getMappings() {
    return mappings;
  }

  public record AsyncCommandNodeActionWithConfig<State extends AgentState>(
      AsyncCommandAction<State> action, Map<String, String> mappings)
      implements AsyncNodeActionWithConfig<State> {

    @Override
    public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
      return CompletableFuture.completedFuture(
          Map.of("command", action, "mappings", mappings));
    }
  }
}
