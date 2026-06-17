package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.spring.ai.agent.skill.SkillResource;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.utils.TypeRef;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Content;
import org.springframework.core.io.ResourceLoader;

import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SubAgentITest {
    static final ResourceLoader resLoader = new org.springframework.core.io.DefaultResourceLoader();

    @Test
    public void testPurchaseAssistantAgent() throws Exception {
        final var rootPath = Paths.get( "target", "checkpoint" );

        final var saver = new FileSystemSaver( rootPath, AgentExecutorEx.State.defaultSerializer());

        //final var chatModel = AiModel.OPENAI.chatModel("gpt-5-mini");
        final var chatModel = AiModel.OLLAMA.chatModel("qwen3.5");

        var compileConfig = CompileConfig.builder()
                .checkpointSaver( saver )
                .build();

        final var subAgentMarketplace = SkilledReactSubAgent.builder()
                .chatModel(chatModel)
                .streaming(true)
                .toolsFromObject( AgentMarketplace.tools() )
                .build( SkillResource.of(resLoader.getResource("classpath:skills/agent-marketplace/" )),
                        compileConfig );

        final var subAgentPayment = SkilledReactSubAgent.builder()
                .chatModel(chatModel)
                .streaming(true)
                .toolsFromObject( AgentPayment.tools() )
                .build( SkillResource.of( resLoader.getResource("classpath:skills/agent-payment/") ),
                        compileConfig );

        final var purchaseAgent = AgentExecutorEx.builder()
                .chatModel(chatModel)
                .streaming(true)
                .tool( subAgentMarketplace )
                .tool( subAgentPayment)
                .build(compileConfig);

        final var diagram = purchaseAgent.getGraph( GraphRepresentation.Type.PLANTUML, "Purchase Assistant Agent", false );
        System.out.println(diagram.content());

        final var runnableConfig = RunnableConfig.builder()
                .threadId("agent-marketplace-002")
                .build();

        final var input = """
        search for product 'X'.
        If found proceed to payment with IBAN US82WEST1234567890123456 and purchase it
        """;

        final var result = purchaseAgent.invoke( GraphInput.args( Map.of("messages", new UserMessage(input) )), runnableConfig);

        assertTrue( result.isPresent() );

        System.out.println(result.get());

    }

    @Test
    public void testCommitAssistantAgent() throws Exception {

        final var logHook = new LogNodeHook(System.out::println);


        final var rootPath = Paths.get( "target", "testCommitSubAgent" );

        final var saver = new FileSystemSaver( rootPath, AgentExecutorEx.State.defaultSerializer());

        var compileConfig = CompileConfig.builder()
                .checkpointSaver( saver )
                .build();

        //final var chatModel = AiModel.OPENAI.chatModel("gpt-5-mini");
        final var chatModel = AiModel.OLLAMA.chatModel("qwen3.5");

        final var agentCommit = AgentExecutorEx.builder()
                .chatModel(chatModel)
                .streaming(true)
                .defaultSystem("""
                        You are a commit assistant.
                        
                        run tool `git-list-files` to get list of staging files.
                        For each file:
                         - involve `agent-commit`  to get the commit message for such file.
                         - repeat for the next file
                        
                        """)
                .tools( AgentCommitAssistant.Tools.get().stream()
                        .filter( t -> "git-list-files".equals(t.getToolDefinition().name()))
                        .toList())
                .tool( AgentCommitAssistant.subAgent(
                        chatModel,
                        compileConfig,
                        SkillResource.of( resLoader.getResource("classpath:skills/agent-commit/")), System.out::println))
                .addNodeHook( logHook.asBeforeCall() )
                .addNodeHook( logHook.asAfterCall() )
                .build( compileConfig );

        final var diagram = agentCommit.getGraph( GraphRepresentation.Type.PLANTUML, "Agent commit Assistant", false );
        System.out.println(diagram.content());

        var runnableConfig = RunnableConfig.builder()
                .threadId("commitAgent001")
                .build();

        final var input = """
                          run commit process
                          """;

        var iterator = agentCommit.stream(
                GraphInput.args( Map.of("messages", new UserMessage(input) )),
                runnableConfig);

        while( true ) {

            try {
                final var result =  iterator.toCompletableFuture()
                                        .thenApply(GraphResult::from)
                                        .join();
                assertFalse(result.isEmpty());

                if( result.isInterruptionMetadata() ) {

                    final var interruptionMetadata = result.asInterruptionMetadata();

                    interruptionMetadata.metadata("TOOL_CALLS", new TypeRef<AssistantMessage.ToolCall>() {
                    }).ifPresent(toolCall -> {
                        System.out.printf("APPROVE '%s' WITH ARGS '%s'%n",
                                toolCall.name(), toolCall.arguments());
                    });
                    iterator = agentCommit.stream(
                            GraphInput.resume(Map.of(AgentEx.APPROVAL_RESULT,
                                    AgentEx.ApprovalState.APPROVED)), runnableConfig);
                    continue;
                }

                final var state = new AgentExecutorEx.State( result.asStateDataOrLastCheckpointStateData() );

                state.lastMessage()
                        .map(Content::getText)
                        .ifPresentOrElse(
                                text -> System.out.printf("commit message:%n%s%n", text),
                                () -> System.out.println("result not found"));
                break;

            }
            catch( Exception e ) {
                saver.release( runnableConfig );
                fail(e);
                break;
            }

        }

    }

}
