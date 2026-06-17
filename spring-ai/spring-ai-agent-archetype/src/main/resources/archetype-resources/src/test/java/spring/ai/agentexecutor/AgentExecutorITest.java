#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agentexecutor;

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
import static java.util.Optional.ofNullable;
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

            System.out.printf("${symbol_escape}nnode start: '%s' with state: %s", nodeId, state);

            return action.apply( state, config ).whenComplete( (result, ex ) -> {

                if( ex != null ) {
                    return;
                }

                System.out.printf("${symbol_escape}nnode end: '%s' with result: %s", nodeId, result);

            });
        }

        @Override
        public CompletableFuture<Command> applyWrap(String nodeId, S state, RunnableConfig config, AsyncCommandAction<S> action) {

            System.out.printf("${symbol_escape}nnode start: '%s' with state: %s%n", nodeId, state);

            return action.apply(state, config).whenComplete((result, ex) -> {

                if (ex != null) {
                    return;
                }

                System.out.printf("${symbol_escape}nnode end: '%s' with result: %s", nodeId, result);

            });
        }

    }

    public record Streaming( boolean active, boolean emitStreamingEnd ) {
        public static Streaming NONE = new Streaming(false, false);
        public static Streaming PARTIAL = new Streaming(true, false);
        public static Streaming FULL = new Streaming(true, true);
    }

    public interface RunAgentCall {
        String userMessage();
        default Streaming streaming() {
            return Streaming.FULL ;
        }
    }

    public enum RunAgentEnum implements RunAgentCall {
        twiceTest {
            @Override
            public String userMessage() {
                return """
                perform test twice with message 'this is a test' and reports their results
                """;
            }

        },
        twiceTestAndThreadCount {
            @Override
            public String userMessage() {
                return """
                perform test twice with message 'this is a test' and reports their results and also number of current active threads
                """;
            }
        }
        ;

    }

    @ParameterizedTest
    @EnumSource(RunAgentEnum.class)
    public void runAgentExecutor(RunAgentCall call) throws Exception {

        var saver = new MemorySaver();

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        final var agent = AgentExecutor.builder()
                .chatModel(chatModel)
                .streaming(call.streaming().active())
                .emitStreamingEnd(call.streaming().emitStreamingEnd())
                .toolsFromObject(
                        // FIX for GEMINI MODEL
                        (chatModel instanceof GoogleGenAiChatModel ) ?
                                new TestTools4Gemini() :
                                new TestTools())
                .build()
                .compile(compileConfig);

        System.out.println(agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String, Object> input = Map.of("messages", new UserMessage(call.userMessage()));
        var runnableConfig = RunnableConfig.empty();

        var result = agent.stream( GraphInput.args(input), runnableConfig);

        var output = result.stream()
                .peek( o -> {
                    if (o instanceof StreamingOutput<?> out) {
                        if( ofNullable(out.chunk()).map(String::isBlank).orElse(false) ) {
                            return;
                        }
                    }
                    System.out.println(o);

                })
                .reduce((a, b) -> b)
                .orElseThrow();

        System.out.printf("result: %s%n",
                output.state().lastMessage()
                        .map(AssistantMessage.class::cast)
                        .map(AssistantMessage::getText)
                        .orElseThrow());

    }

    @ParameterizedTest
    @EnumSource(RunAgentEnum.class)
    public void runAgentExecutorEx(RunAgentCall call) throws Exception {

        final var saver = new MemorySaver();

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

       final var agent = AgentExecutorEx.builder()
                .chatModel(chatModel)
                .streaming(call.streaming().active())
                .emitStreamingEnd(call.streaming().emitStreamingEnd())
                .toolsFromObject(
                        // FIX for GEMINI MODEL
                        (chatModel instanceof GoogleGenAiChatModel ) ?
                            new TestTools4Gemini() :
                            new TestTools())
                .build(compileConfig);

        System.out.println(agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String, Object> input = Map.of("messages", new UserMessage(call.userMessage()));
        var runnableConfig = RunnableConfig.empty();

        var result = agent.stream( GraphInput.args(input), runnableConfig);

        var output = result.stream()
                .peek( o -> {
                    if (o instanceof StreamingOutput<?> out) {
                        final var chunk = out.chunk();
                        if( chunk == null || chunk.isBlank() ) {
                            return;
                        }
                    }
                    System.out.println(o);
                })
                .reduce((a, b) -> b)
                .orElseThrow();

        System.out.printf("result: %s%n",
                output.state().lastMessage()
                        .map(AssistantMessage.class::cast)
                        .map(AssistantMessage::getText)
                        .orElseThrow());

    }

    public interface RunAgentWithApprovalCall extends RunAgentCall{
        String userMessage();
        default boolean approve() {
            return true;
        };
    }

    public enum RunAgentWithApprovalEnum implements RunAgentWithApprovalCall {
        twiceTestAndThreadCountApproved {
            @Override
            public String userMessage() {
                return """
                perform test twice with message 'this is a test' and
                reports their results and also number of current active threads
                """;
            }
        },
        twiceTestAndThreadCountDenied {
            @Override
            public String userMessage() {
                return """
                perform test twice with message 'this is a test' and
                reports their results and also number of current active threads
                """;
            }
            @Override
            public boolean approve() {
                return false;
            }

        };
    }

    @ParameterizedTest
    @EnumSource(RunAgentWithApprovalEnum.class)
    public void runAgentWithApproval(RunAgentWithApprovalCall call) throws Exception {

        final var saver = new MemorySaver();

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        final var agent = AgentExecutorEx.builder()
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

        //System.out.println(agent.getGraph(GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        var runnableConfig = RunnableConfig.empty();

        var input = GraphInput.args( Map.of("messages", new UserMessage(call.userMessage())) );

        while (true) {
            final var result = agent.stream(input, runnableConfig)
                    .forEachAsync( s -> {
                        if (s instanceof StreamingOutput<?> out  ) {
                            if( !out.chunk().isEmpty() ) {
                                System.out.printf("%s: (%s)${symbol_escape}n", out.node(), out.chunk());
                            }
                        } else {
                            System.out.println(s.node());
                        }
                    })
                    .thenApply(GraphResult::from)
                    .join();

            if( result.isInterruptionMetadata() ) {
                final var interruption = result.asInterruptionMetadata();

                System.out.printf( "%s%n", interruption.metadata("label").orElse("Approve action ?"));

                input = ( call.approve() ) ?
                        GraphInput.resume(Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.APPROVED)) :
                        GraphInput.resume(Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.REJECTED)) ;

            }
            else {

                final var state = new AgentExecutorEx.State(result.asStateDataOrLastCheckpointStateData());

                System.out.println( """
                =============================
                MESSAGES
                =============================
                """);
                state.messages().forEach(System.out::println);

                System.out.println( """
                =============================
                TOOL EXECUTION RESPONSES
                =============================
                """);
                state.toolExecutionResponses().forEach(System.out::println);
                break;
            }

        }
    }

    public void runAgentWithInterruption(RunAgentCall call) throws Exception {

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

    public void runAgentWithCancellation(RunAgentCall call) throws Exception {

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
                        System.out.printf("%s: (%s)${symbol_escape}n", out.node(), out.chunk());
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
