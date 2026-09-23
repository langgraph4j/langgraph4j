package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.ToolCallChunkEvent;
import com.agui.community.core.interrupt.Interrupt;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.json.AGUIJacksonSerializer;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;

public class AGUIAgentExecutorINTERRUPT extends AGUIAgentBase {

    private final MemorySaver saver = new MemorySaver();

    private final com.agui.community.core.serialization.Serializer aguiSerializer =
            new AGUIJacksonSerializer();

    public AGUIAgentExecutorINTERRUPT(String id) {
        super(id);
    }

    @Override
    protected CompiledGraph<? extends AgentState> newGraph() throws Exception {
        var graph = AgentExecutorEx.builder()
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
                graph.getGraph(GraphRepresentation.Type.PLANTUML, "Agent Executor", false).content()
        );

        var compileConfig = CompileConfig.builder().checkpointSaver(saver).build();

        return graph.compile(compileConfig);

    }

    @Override
    protected GraphInput graphInput(RunAgentInput input) {
        if (!input.resume().isEmpty()) {

            if (input.resume().size() > 1) {
                throw new IllegalStateException("resume more than one message");
            }
            final var resume = input.resume().get(0);

            @SuppressWarnings("unchecked")
            final var payload = (Map<String, Object>) resume.payload();

            final var approvalResult = payload.get(AgentEx.APPROVAL_RESULT);

            return GraphInput.resume(Map.of(AgentEx.APPROVAL_RESULT, approvalResult));

        }

        var lastUserMessage = lastOf(input.messages())
                .map(com.agui.community.core.message.Message::content)
                .orElseThrow(() -> new IllegalStateException("last user message not found"));

        log.debug("LAST USER MESSAGE: {}", lastUserMessage);

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

            var interrupts = lastOf(messages)
                    .flatMap(MessageUtil::asAssistantMessage)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls)
                    .map(toolCalls ->
                            toolCalls.stream().map(toolCall -> {
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

                                final var metadata = aguiSerializer.deserialize(toolCall.arguments(), Map.class);
                                return new Interrupt("int-1",
                                        interruptionMetadata.reason().orElse(""),
                                        "",
                                        toolCallId,
                                        null,
                                        null,
                                        metadata
                                        );
                            }).toList());
            final var output = outputBuilder
                    .addEvent(new RunFinishedEvent(
                            input.threadId(),
                            input.runId(),
                            new InterruptOutcome(interrupts.orElseGet(List::of)),
                            null,
                            System.currentTimeMillis(),
                            null))
                    .build(interruptionMetadata.nodeId(), interruptionMetadata.state());
            return output.events();

        }

        final var output = outputBuilder
                .addEvent(new RunFinishedEvent(
                        input.threadId(),
                        input.runId(),
                        new SuccessOutcome(),
                        null,
                        System.currentTimeMillis(),
                        null))
                .build(END, graph.stateFactory().apply(result.asStateDataOrLastCheckpointStateData()));
        return output.events();

    }

}
