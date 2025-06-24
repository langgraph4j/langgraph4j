package org.bsc.langgraph4j.internal.node;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;

public class CommandNode<State extends AgentState> extends Node<State> {

  private final Map<String, String> mappings;
  private final AsyncCommandAction<State> action;

  public CommandNode(String id, AsyncCommandAction<State> action, Map<String, String> mappings) {
    super(id, (config) -> new AsyncCommandNodeActionWithConfig<>(action));
    this.mappings = mappings;
    this.action = action;
  }

  public Map<String, String> mappings() {
    return mappings;
  }

  public CommandNode<State> withSourceAndTargetIdsUpdated(Node<State> subgraphNode,
                                                          Function<String,String> newSourceId,
                                                          Function<String,String> newTargetId ) {
    var newId  = newSourceId.apply(id());

    var newMappings = mappings().entrySet().stream()
            .collect(Collectors.toMap( Map.Entry::getKey,
                    e -> {
                      var id = newTargetId.apply( e.getValue() );
                      return ( id != null ) ? id : e.getValue();
                    }));
    return new CommandNode<>( newId, action, newMappings );

  }

  private record AsyncCommandNodeActionWithConfig<State extends AgentState>(
          AsyncCommandAction<State> action) implements AsyncNodeActionWithConfig<State> {

    @Override
    public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
      return action.apply(state, config).thenApply(command -> Map.of("command", command) );
    }
  }
}
