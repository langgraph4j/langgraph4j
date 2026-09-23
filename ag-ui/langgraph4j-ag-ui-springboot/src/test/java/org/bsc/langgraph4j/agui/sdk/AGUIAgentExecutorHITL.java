package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.*;
import com.agui.community.core.interrupt.SuccessOutcome;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.spring.ai.util.MessageUtil;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.*;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;

public class AGUIAgentExecutorHITL extends AGUIAgentBase {

    private final MemorySaver saver = new MemorySaver();

    public AGUIAgentExecutorHITL(String id) {
        super(id);
    }

    @Override
    protected CompiledGraph<? extends AgentState> newGraph() throws Exception {
        var agent = AgentExecutorEx.builder()
                .chatModel(AiModel.OLLAMA.chatModel("qwen3.5"))
                .emitStreamingEnd(true)
                .streaming(true)
                .toolsFromObject(new Tools())
                .approvalOn("sendEmail",
                        (nodeId, state) ->
                                InterruptionMetadata.builder(nodeId, state)
                                        .build()
                )
                .build();

        log.info("REPRESENTATION:\n{}",
                agent.getGraph(GraphRepresentation.Type.PLANTUML, "Agent Executor", false).content()
        );

        var compileConfig = CompileConfig.builder().checkpointSaver(saver).build();

        return agent.compile(compileConfig);
    }

    @Override
    protected GraphInput graphInput(RunAgentInput input) {
        var lastUserMessage = lastOf(input.messages())
                .map(com.agui.community.core.message.Message::content)
                .orElseThrow(() -> new IllegalStateException("last user message not found"));

        log.debug("LAST USER MESSAGE: {}", lastUserMessage);

        if (input.state() instanceof Map<?, ?> state && Boolean.TRUE.equals(state.get("resume"))) {
            return GraphInput.resume(Map.of(AgentEx.APPROVAL_RESULT, lastUserMessage));
        }

        return GraphInput.args(Map.of("messages", new UserMessage(lastUserMessage)));
    }

    @Override
    protected Collection<? extends Event> onCompleteEvents(RunAgentInput input, GraphResult result) {

        final var outputBuilder = AGUINodeOutput.builder();

        if (result.isInterruptionMetadata()) {

            final var interruptionMetadata = result.asInterruptionMetadata();

            log.trace("INTERRUPTION DETECTED: {}", interruptionMetadata);

            final var messages = interruptionMetadata.state().<List<Message>>value("messages")
                    .orElseThrow(() -> new IllegalStateException("messages not found into given state"));


            lastOf(messages)
                    .flatMap(MessageUtil::asAssistantMessage)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls)
                    .ifPresent(toolCalls ->
                            toolCalls.forEach(toolCall -> {
                                var toolCallId = toolCall.id().isBlank() ?
                                        UUID.randomUUID().toString() :
                                        toolCall.id();

                                outputBuilder.addEvent(new ToolCallChunkEvent(
                                        toolCallId,
                                        toolCall.name(),
                                        null,
                                        toolCall.arguments(),
                                        System.currentTimeMillis(),
                                        null
                                ));
                            }));
            final var output = outputBuilder
                    .addEvent(new StateDeltaEvent(
                            List.of(new JsonPatchOperation("add", "/resume", true)),
                            System.currentTimeMillis(),
                            null))
                    .addEvent(new RunFinishedEvent(
                            input.threadId(),
                            input.runId(),
                            new SuccessOutcome(),
                            null,
                            System.currentTimeMillis(),
                            null))
                    .build(interruptionMetadata.nodeId(), interruptionMetadata.state());
            return output.events();
        }

            final var output =  outputBuilder
                    .addEvent(new StateDeltaEvent(
                            List.of(
                                new JsonPatchOperation("add", "/resume", false),
                                new JsonPatchOperation( "remove", "/resume", null )
                            ),
                            System.currentTimeMillis(),
                            null))
                    .addEvent(new RunFinishedEvent(
                            input.threadId(),
                            input.runId(),
                            new SuccessOutcome(),
                            null,
                            System.currentTimeMillis(),
                            null))
                    .build(END, graph.stateFactory().apply( result.asStateDataOrLastCheckpointStateData() ));
            return output.events();


    }
}
