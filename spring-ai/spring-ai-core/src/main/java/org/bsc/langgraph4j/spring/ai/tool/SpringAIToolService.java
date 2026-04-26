package org.bsc.langgraph4j.spring.ai.tool;

import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.agent.ToolResponseBuilder.COMMAND_RESULT;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

/**
 * Service class responsible for managing tools and their callbacks.
 */
public class SpringAIToolService {

    private final List<ToolCallback> agentFunctions;

    public SpringAIToolService(List<ToolCallback> agentFunctions ) {
        this.agentFunctions = agentFunctions;
    }

    /**
     * Retrieves all registered function callback wrappers.
     *
     * @return A list of function callback wrappers.
     */
    public List<ToolCallback> agentFunctionsCallback() {
        return agentFunctions;
    }

    /**
     * Retrieves a specific function callback wrapper by name.
     *
     * @param name The name of the function callback to retrieve.
     * @return An optional containing the function callback wrapper, or an empty optional if not found.
     */
    public Optional<ToolCallback> agentFunction( String name ) {
        requireNonNull( name, "name cannot be null" );

        return this.agentFunctions.stream()
                .filter( tool -> Objects.equals(tool.getToolDefinition().name(), name ))
                .findFirst();
    }


    public record ExecuteFunctionsResult(
            List<ToolResponseMessage.ToolResponse> toolResponses,
            Command command
    ){}

    public CompletableFuture<ExecuteFunctionsResult> executeFunctions(List<AssistantMessage.ToolCall> toolCalls,
                                                                      Map<String,Object> toolContextData)
    {

        final var toolResponses = new ArrayList<ToolResponseMessage.ToolResponse>(toolCalls.size());

        var update = Map.<String,Object>of();
        String gotoNode = null;

        for( var toolCall : toolCalls ) {

            var toolCallback = agentFunction( toolCall.name() );

            if( toolCallback.isEmpty() ) {
                return failedFuture( new IllegalStateException( format("No tool callback found for name: %s", toolCall.name())) );
            }

            var scopedToolTaskResult = scopedToolCall( toolCallback.get(), toolCall, toolContextData );
            var command = scopedToolTaskResult.command();

            if( command.gotoNodeSafe().isPresent() ) {
                if( gotoNode != null ) {
                    return failedFuture( new IllegalStateException( format("Multiple nodes target provided! tried to set %s when %s was already present : ",
                            command.gotoNode(),
                            gotoNode) ));
                }
                gotoNode = command.gotoNode();
            }

            update = mergeMap( update, command.update(), (v1,v2) -> v2  );

            toolResponses.add( scopedToolTaskResult.toolResponse() );

        }

        return completedFuture( new ExecuteFunctionsResult(toolResponses, new Command(gotoNode, update)) );
    }

    /**
     * Executes a list of tool calls.
     *
     * @param toolCalls The list of tool calls to execute.
     * @param toolContextData The tool context data.
     * @param propertyNameToUpdate name of the state property where ste the tool response message
     * @return A completable future that will be completed with the tool response message.
     */
    public CompletableFuture<Command> executeFunctions(List<AssistantMessage.ToolCall> toolCalls, Map<String,Object> toolContextData, String propertyNameToUpdate) {
        if( propertyNameToUpdate == null  ) {
            return failedFuture(new NullPointerException("propertyName cannot be null"));
        }
        if( propertyNameToUpdate.isEmpty()  ) {
            return failedFuture(new IllegalArgumentException("propertyName cannot be empty") );
        }

        return executeFunctions( toolCalls, toolContextData )
                .thenApply( result -> {
                    final var toolResponseMessage = ToolResponseMessage.builder()
                    .responses( result.toolResponses )
                    .build();

                    return result.command.withMergedUpdate( Map.of(propertyNameToUpdate, toolResponseMessage) );
                });

    }


    private record ScopedToolCallResult(
        ToolResponseMessage.ToolResponse toolResponse,
        Command command
    ){
        public Command command() {
            return ofNullable(command).orElseGet(Command::emptyCommand);
        }

        ScopedToolCallResult {
            requireNonNull( toolResponse, "response cannot be null");
        }
    }

    private ScopedToolCallResult scopedToolCall(ToolCallback toolCallback,
                                                AssistantMessage.ToolCall toolCall,
                                                Map<String,Object> toolContextData  )
    {
        final var scopedCommandResult = new AtomicReference<Command>();
        final var context = mergeMap(
                requireNonNull(toolContextData, "state cannot be null!"),
                Map.of(COMMAND_RESULT, scopedCommandResult),
                (v1, v2) -> v2);

        final var toolContext = new ToolContext( context );

        var toolResult = toolCallback.call(toolCall.arguments(), toolContext );

        var toolResponse = new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult);

        return new ScopedToolCallResult( toolResponse, scopedCommandResult.get() );
    }

}
