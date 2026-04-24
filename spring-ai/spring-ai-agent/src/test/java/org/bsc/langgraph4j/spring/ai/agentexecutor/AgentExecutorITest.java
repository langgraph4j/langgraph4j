package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.gemini.TestTools4Gemini;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class AgentExecutorITest {

    @Autowired
    private ChatModel chatModel;
    @Autowired
    private ResourceLoader resourceLoader;

    static class WrapCallLogHook<S extends MessagesState<Message>> implements NodeHook.WrapCall<S>, EdgeHook.WrapCall<S> {

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId,
                                                                S state,
                                                                RunnableConfig config,
                                                                AsyncNodeActionWithConfig<S> action) {

            System.out.printf("\nnode start: '%s' with state: %s", nodeId, state);

            return action.apply( state, config ).whenComplete( (result, ex ) -> {

                if( ex != null ) {
                    return;
                }

                System.out.printf("\nnode end: '%s' with result: %s", nodeId, result);

            });
        }

        @Override
        public CompletableFuture<Command> applyWrap(String nodeId, S state, RunnableConfig config, AsyncCommandAction<S> action) {

            System.out.printf("\nnode start: '%s' with state: %s%n", nodeId, state);

            return action.apply(state, config).whenComplete((result, ex) -> {

                if (ex != null) {
                    return;
                }

                System.out.printf("\nnode end: '%s' with result: %s", nodeId, result);

            });
        }

    }

    public record Streaming( boolean active, boolean emitStreamingEnd ) {
        public static Streaming NONE = new Streaming(false, false);
        public static Streaming PARTIAL = new Streaming(true, false);
        public static Streaming FULL = new Streaming(true, true);
    }

    public interface Call {
        String userMessage();
        Streaming streaming();
    }

    public enum RunAgentCall implements Call {
        twiceTest {
            @Override
            public String userMessage() {
                return """
                perform test twice with message 'this is a test' and reports their results
                """;
            }
            @Override
            public Streaming streaming() {
                return Streaming.FULL ;
            }

        },
        twiceTestAndThreadCount {
            @Override
            public String userMessage() {
                return """
                perform test twice with message 'this is a test' and reports their results and also number of current active threads
                """;
            }
            @Override
            public Streaming streaming() {
                return Streaming.FULL ;
            }
        }
        ;

    }

    @ParameterizedTest
    @EnumSource(RunAgentCall.class)
    public void runAgent(Call call) throws Exception {

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var agentBuilder = AgentExecutor.builder()
                .chatModel(chatModel)
                .streaming(call.streaming().active())
                .emitStreamingEnd(call.streaming().emitStreamingEnd());

        // FIX for GEMINI MODEL
        if (chatModel instanceof GoogleGenAiChatModel ) {
            agentBuilder.toolsFromObject(new TestTools4Gemini());
        } else {
            agentBuilder.toolsFromObject(new TestTools());
        }

        var agent = agentBuilder.build().compile(compileConfig);

        System.out.println(agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String, Object> input = Map.of("messages", new UserMessage(call.userMessage()));
        var runnableConfig = RunnableConfig.empty();

        var result = agent.stream( GraphInput.args(input), runnableConfig);

        var output = result.stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .orElseThrow();

        System.out.printf("result: %s%n",
                output.state().lastMessage()
                        .map(AssistantMessage.class::cast)
                        .map(AssistantMessage::getText)
                        .orElseThrow());

    }

    public void runAgentWithApproval(Call call) throws Exception {

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var agent = AgentExecutorEx.builder()
                .chatModel(chatModel)
                .streaming(call.streaming().active())
                .emitStreamingEnd(call.streaming().emitStreamingEnd())
                .toolsFromObject(new TestTools()) // Support without providing tools
                .approvalOn("threadCount", (nodeId, state) ->
                        InterruptionMetadata.builder(nodeId, state)
                                .addMetadata("label", "confirm thread count execution?")
                                .build())
                .build()
                .compile(compileConfig);

        System.out.println(agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String, Object> input = Map.of("messages", new UserMessage(call.userMessage()));

        var runnableConfig = RunnableConfig.empty();

        while (true) {
            var result = agent.stream(GraphInput.args(input), runnableConfig);

            var output = result.stream()
                    .peek(s -> {
                        if (s instanceof StreamingOutput<?> out) {
                            System.out.printf("%s: (%s)\n", out.node(), out.chunk());
                        } else {
                            System.out.println(s.node());
                        }
                    })
                    .reduce((a, b) -> b)
                    .orElseThrow();

            if (output.isEND()) {
                System.out.printf("result: %s%n", output.state());
                break;

            } else {

                var returnValue = AsyncGenerator.resultValue(result);

                if (returnValue.isPresent()) {

                    System.out.printf("interrupted: %s%n", returnValue.orElse("NO RESULT FOUND!"));

                    if (returnValue.get() instanceof InterruptionMetadata<?> interruption) {

                        var answer = System.console().readLine(format("%s : (N\\y) \t\n", interruption.metadata("label").orElse("Approve action ?")));

                        if (Objects.equals(answer, "Y") || Objects.equals(answer, "y")) {
                            runnableConfig = agent.updateState(runnableConfig, Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.APPROVED.name()));
                        } else {
                            runnableConfig = agent.updateState(runnableConfig, Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.REJECTED.name()));
                        }
                    }
                    input = null;
                }

            }

        }
    }

    public void runAgentWithInterruption(Call call) throws Exception {

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptAfter("threadCount")
                .interruptBeforeEdge( true )
                .build();

        var agentBuilder = AgentExecutorEx.builder()
                .chatModel(chatModel)
                .streaming(call.streaming().active())
                .emitStreamingEnd(call.streaming().emitStreamingEnd());

        // FIX for GEMINI MODEL
        if (chatModel instanceof GoogleGenAiChatModel) {
            agentBuilder.toolsFromObject(new TestTools4Gemini());
        } else {
            agentBuilder.toolsFromObject(new TestTools());
        }

        var agent = agentBuilder.build().compile(compileConfig);

        System.out.println(agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String, Object> input = Map.of("messages", new UserMessage(call.userMessage()));
        var runnableConfig = RunnableConfig.empty();

        var iterator = agent.stream(GraphInput.args(input), runnableConfig);

        var output = iterator.stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .orElseThrow();

        final var result = GraphResult.from(iterator);

        assertTrue( result.isInterruptionMetadata() );

        final var interruptionMetadata = result.<AgentExecutorEx.State>asInterruptionMetadata();

        final var lastMessage = interruptionMetadata.state().lastMessage();
        assertTrue( lastMessage.isPresent() );
        assertInstanceOf( ToolResponseMessage.class, lastMessage.get() );

        System.out.printf("result: %s%n", result);

    }

    public void runAgentWithCancellation(Call call) throws Exception {

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var agentBuilder = AgentExecutor.builder()
                .chatModel(chatModel)
                .streaming(call.streaming().active())
                .emitStreamingEnd(call.streaming().emitStreamingEnd())
                ;

        // FIX for GEMINI MODEL
        if (chatModel instanceof GoogleGenAiChatModel) {
            agentBuilder.toolsFromObject(new TestTools4Gemini());
        } else {
            agentBuilder.toolsFromObject(new TestTools());
        }

        var agent = agentBuilder.build().compile(compileConfig);

        System.out.println(agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String, Object> input = Map.of("messages", new UserMessage(call.userMessage()));

        var runnableConfig = RunnableConfig.empty();

        var generator = agent.stream(GraphInput.args(input), runnableConfig);


        var future = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
                generator.cancel(true);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        var output = generator.stream()
                .peek(s -> {
                    if (s instanceof StreamingOutput<?> out) {
                        System.out.printf("%s: (%s)\n", out.node(), out.chunk());
                    } else {
                        System.out.println(s.node());
                    }
                })
                .reduce((a, b) -> b)
                .orElseThrow();


        future.get();

        if (!generator.isCancelled()) {
            System.out.printf("generator lastState: %s%n",
                    output.state().lastMessage()
                            .map(AssistantMessage.class::cast)
                            .map(AssistantMessage::getText)
                            .orElseThrow());
        } else {
            var result = AsyncGenerator.resultValue(generator).orElse("<None>");
            System.out.printf("generator execution has been cancelled on node: '%s' with result: %s%n", output.node(), result);
        }
    }

    public void runAgentWithSkill() throws Exception {

        final var hook = new WrapCallLogHook<AgentExecutor.State>();

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .recursionLimit(10)
                .checkpointSaver(saver)
                .build();

        final var skills = SkillsTool.builder()
                            .addSkillsResource( resourceLoader.getResource("classpath:skills"))
                            .build();

        var agent = AgentExecutor.builder()
                .addCallModelHook( hook )
                .addExecuteToolsHook( hook )
                .chatModel(chatModel)
                .defaultSystem("Always use the available skills to assist the user in their requests.")
                .tool( skills )
                .tools(List.of(ToolCallbacks.from(FileSystemTools.builder().build())))
                .tools(List.of(ToolCallbacks.from(ShellTools.builder().build())))
                .build()
                .compile(compileConfig);

        System.out.println( agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        final var userMessage = """
					update changelog in the current folder.
					Use required skills.
					Use absolute paths for the skills and scripts. Do not ask me for more details.
					""";
        var input = GraphInput.args(Map.of("messages", new UserMessage(userMessage)));

        var runnableConfig = RunnableConfig.empty();

        var generator = agent.stream(input, runnableConfig);

        var output = generator.stream()
                .peek(s -> {
                    if (s instanceof StreamingOutput<?> out) {
                        System.out.printf("%s: (%s)%n", out.node(), out.chunk());
                    } else {
                        System.out.println(s.node());
                    }
                })
                .reduce((a, b) -> b)
                .orElseThrow();
    }

    public void runAgentExWithSkill() throws Exception {

        final var hook = new WrapCallLogHook<AgentExecutorEx.State>();

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        final var skills = SkillsTool.builder()
                .addSkillsResource( resourceLoader.getResource("classpath:skills"))
                .build();

        var agent = AgentExecutorEx.builder()
                .addCallModelHook( hook )
                .addApprovalActionHook( hook )
                .addDispatchActionHook( hook )
                .addShouldContinueHook( hook )
                .addDispatchToolsHook( hook )
                .chatModel(chatModel)
                .defaultSystem("Always use the available skills to assist the user in their requests.")
                .tool( skills )
                .tools(List.of(ToolCallbacks.from(FileSystemTools.builder().build())))
                .tools(List.of(ToolCallbacks.from(ShellTools.builder().build())))
                .build()
                .compile(compileConfig);

        System.out.println( agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        final var userMessage = """
					update changelog in the current folder.
					Use required skills.
					Use absolute paths for the skills and scripts. Do not ask me for more details.
					""";
        var input = GraphInput.args(Map.of("messages", new UserMessage(userMessage)));

        var runnableConfig = RunnableConfig.empty();

        var generator = agent.stream(input, runnableConfig);

        var output = generator.stream()
                .peek(s -> {
                    if (s instanceof StreamingOutput<?> out) {
                        System.out.printf("%s: (%s)%n", out.node(), out.chunk());
                    } else {
                        System.out.println(s.node());
                    }
                })
                .reduce((a, b) -> b)
                .orElseThrow();
    }

}
