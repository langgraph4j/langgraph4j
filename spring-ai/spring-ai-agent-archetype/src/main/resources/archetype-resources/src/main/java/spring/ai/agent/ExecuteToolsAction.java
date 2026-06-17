#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agent;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.tool.SpringAIToolService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

public class ExecuteToolsAction<State extends MessagesState<Message>> implements AsyncCommandAction<State> {

    final SpringAIToolService toolService;

    public ExecuteToolsAction( List<ToolCallback> tools ) {
        toolService = new SpringAIToolService( requireNonNull( tools, "tools cannot be null" ));
    }

    @Override
    public CompletableFuture<Command> apply(State state, RunnableConfig runnableConfig) {

        final var message = state.lastMessage();

        if (message.isEmpty()) {
            return failedFuture(new IllegalArgumentException("no input provided!"));
        }

        if (message.get() instanceof AssistantMessage assistantMessage) {

            if (assistantMessage.hasToolCalls()) {

                return toolService.executeFunctions(assistantMessage.getToolCalls(), state.data(), MessagesState.MESSAGES_STATE)
                        .thenApply( command -> {
                            if( command.gotoNodeSafe().isPresent() ) {
                                return command;
                            }

                            return new Command(Agent.AGENT_LABEL, command.update() );

                        });

            }
            else {
//                var finishReason = message.get().getMetadata().getOrDefault("finishReason", "");

//                if (Objects.equals( Objects.toString(finishReason), "STOP")) {
//                    return completedFuture(new Command(Agent.END_LABEL ));
//                }

                return completedFuture(new Command(Agent.END_LABEL ));

            }
        }

        return failedFuture(new IllegalArgumentException("no AssistantMessage provided!"));
    }
}
