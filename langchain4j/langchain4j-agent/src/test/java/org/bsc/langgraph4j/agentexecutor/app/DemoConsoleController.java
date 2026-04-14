package org.bsc.langgraph4j.agentexecutor.app;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.agentexecutor.TestTools;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Objects;

import static java.lang.String.format;

/**
 * Demonstrates the use of Spring Boot CLI to execute a task using an agent executor.
 */
@Controller
public class DemoConsoleController implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemoConsoleController.class);

    private final ChatModel chatModel;

    public DemoConsoleController( ChatModel chatModel ) {

        this.chatModel = chatModel;
    }

    /**
     * Executes the command-line interface to demonstrate a Spring Boot application.
     * This method logs a welcome message, constructs a graph using an agent executor,
     * compiles it into a workflow, invokes the workflow with a specific input,
     * and then logs the final result.
     *
     * @param args Command line arguments (Unused in this context)
     * @throws Exception If any error occurs during execution
     */
    @Override
    public void run(String... args) throws Exception {

        log.info("Welcome to the Spring Boot CLI application!");

        var console = System.console();

        var userMessage = "perform a test";

        var streaming = true;

        runAgent( userMessage, console  );

        runAgentWithApproval( userMessage, console  );
    }

    public void runAgentWithApproval( String userMessage, java.io.Console console ) throws Exception {

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var agent = AgentExecutorEx.builder()
                .chatModel(chatModel)
                .toolsFromObject( new TestTools()) // Support without providing tools
                .approvalOn( "execTest", ( nodeId, state ) ->
                        InterruptionMetadata.builder( nodeId, state )
                                .addMetadata( "label", "confirm execution of test?")
                                .build())

                .build()
                .compile(compileConfig);

        log.info( "{}", agent.getGraph( GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String,Object> input = Map.of("messages", UserMessage.from(userMessage) );

        var runnableConfig = RunnableConfig.builder().build();

        while( true ) {
            var result = agent.stream(input, runnableConfig );

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
                console.format( "result: %s\n",
                        output.state().finalResponse().orElseThrow());
                break;

            } else {

                var returnValue = GraphResult.from(result);

                if( !returnValue.isEmpty() ) {

                    log.info("interrupted: {}", result);

                    if (returnValue.isInterruptionMetadata()) {

                        var answer = console.readLine(format("%s : (N\\y) \t\n", returnValue.asInterruptionMetadata().metadata("label").orElse("Approve action ?")));

                        if (Objects.equals(answer, "Y") || Objects.equals(answer, "y")) {
                            runnableConfig = agent.updateState(runnableConfig, Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.APPROVED));
                        } else {
                            runnableConfig = agent.updateState(runnableConfig, Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.REJECTED));
                        }
                    }
                    input = null;
                }

            }

        }
    }

    public void runAgent( String userMessage, java.io.Console console ) throws Exception {

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var agent = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject( new TestTools())
                .build()
                .compile(compileConfig);

        log.info( "{}", agent.getGraph( GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String,Object> input = Map.of("messages", new UserMessage(userMessage) );
        var runnableConfig = RunnableConfig.builder().build();

        var result = agent.stream(input, runnableConfig );

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

        console.format( "result: %s\n",
                output.state().finalResponse().orElseThrow());

    }

}