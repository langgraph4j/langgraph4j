//DEPS org.bsc.langgraph4j:langgraph4j-springai-agentexecutor:1.9.0-beta5
//DEPS org.bsc.langgraph4j:langgraph4j-bom:1.9.0-beta5@pom
//DEPS org.bsc.langgraph4j:langgraph4j-core
//DEPS org.bsc.langgraph4j:langgraph4j-javelit
//DEPS org.springframework.ai:spring-ai-bom:1.1.4@pom
//DEPS org.springframework.ai:spring-ai-client-chat
//DEPS org.springframework.ai:spring-ai-openai
//DEPS org.springframework.ai:spring-ai-ollama
//SOURCES org/bsc/langgraph4j/spring/ai/agent/AiModel.java
//SOURCES org/bsc/langgraph4j/spring/ai/agent/AgentMarketplace.java
//SOURCES org/bsc/langgraph4j/spring/ai/agent/AgentPayment.java

import io.javelit.core.Jt;
import io.javelit.core.JtComponent;
import org.bsc.javelit.JtSelectAiModel;
import org.bsc.javelit.JtSessionValue;
import org.bsc.javelit.JtSpinner;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.agent.skill.SkillPath;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.spring.ai.agent.AgentCommitAssistant;
import org.bsc.langgraph4j.spring.ai.agent.AiModel;
import org.bsc.langgraph4j.spring.ai.agent.LogNodeHook;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.bsc.langgraph4j.utils.TypeRef;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Content;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;


/**
 * Javelite App
 *
 *
 */
public class JtCommitAssistantAgentApp {

    public static void main(String[] args) {

        var app = new JtCommitAssistantAgentApp();

        app.view();
    }

    final JtSessionValue<CompiledGraph<AgentExecutorEx.State>> agentSession = new JtSessionValue<>("agent");

    public void view() {

        Jt.title("Commit Assistant Agent").use();
        Jt.markdown("### Powered by LangGraph4j and SpringAI").use();

        var modelOptional = JtSelectAiModel.get();

/*
        var streaming = Jt.toggle("Streaming output")
                .onChange( prev -> agentSession.clear())
                .value(false)
                .use();
*/

        Jt.divider().use();

        if (modelOptional.isEmpty()) return;

        var model = modelOptional.get();

        var chatModel = switch (model.provider()) {
            case OPENAI -> AiModel.OPENAI.chatModel(model.name());
            case GITHUB -> {
                Jt.warning("GITHUB model is not supported yet").use();
                yield null;
            }
            case VERTEX -> {
                Jt.warning("GEMINI model is not supported yet").use();
                yield null;
            }
            case OLLAMA -> AiModel.OLLAMA.chatModel(model.name());
        };

        if (chatModel != null) {

            var userMessage = Jt.textArea("input")
                    .value("start commit process")
                    .placeholder("input")
                    .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
                    .use();
            if (Jt.button("Start").disabled(userMessage.isEmpty()).use()) {

                final var spinner = JtSpinner.builder()
                        .message("**starting the agent** ....")
                        .use();

                final var outputComponent = Jt.expander("Workflow Steps").use();

                try {
                    final var startTime = Instant.now();

                    final var agent = agentSession.computeIfAbsent( key ->
                            buildAgent(chatModel, false, ( msg ) ->
                                    //System.out.println( msg ) ));
                                    Jt.info(msg).use(outputComponent)) );

                    final var runnableConfig = RunnableConfig.builder()
                            .threadId("agent-commit-001")
                            .graphId("commit-assistant")
                            .build();


                    Jt.info("starting agentic workflow").use(outputComponent);

                    var input = GraphInput.args( Map.of("messages", new UserMessage(userMessage)) );
                    while( true ) {

                        final var generator = agent.stream( input, runnableConfig);

                        final var result = generator.toCompletableFuture()
                                            .thenApply(GraphResult::from)
                                            .join();

                        // INTERRUPTED
                        if( result.isInterruptionMetadata() ) {

                            final var interruptionMetadata = result.asInterruptionMetadata();

                            interruptionMetadata.metadata("TOOL_CALLS", new TypeRef<AssistantMessage.ToolCall>() {})
                                    .ifPresent(toolCall ->
                                            Jt.warning( "APPROVE '%s' WITH ARGS '%s'%n".formatted(toolCall.name(), toolCall.arguments()))
                                                .use(outputComponent));

                            input = GraphInput.resume( Map.of(  AgentEx.APPROVAL_RESULT,
                                                                AgentEx.ApprovalState.APPROVED));
                            continue;
                        }

                        // FINISHED
                        if( result.isStateDataOrCheckpointSaverTag() ) {

                            final var state = new AgentExecutorEx.State( result.asStateDataOrLastCheckpointStateData() );

                            final var response = state.lastMessage()
                                    .map(Content::getText)
                                    .orElse("No response found");

                            final var elapsedTime = Duration.between(startTime, Instant.now());

                            Jt.success("finished in %ds%n%n%s".formatted(elapsedTime.toSeconds(), response))
                                    .use(spinner);

                            break;
                        }
                    }

                } catch (Throwable ex) {
                    Jt.error(ex.getMessage()).use(spinner);
                }
            }
        }
    }

    public CompiledGraph<AgentExecutorEx.State> buildAgent(ChatModel chatModel, boolean streaming, Consumer<String> logConsumer )  {

        final var logHook = new LogNodeHook(logConsumer);

        try {
            final var stateSerializer = new SpringAIJacksonStateSerializer<>(AgentExecutorEx.State::new);

            final var rootPath = Paths.get("target", "checkpoint");

            final var saver = new FileSystemSaver(rootPath, stateSerializer);

            var compileConfig = CompileConfig.builder()
                    .checkpointSaver(saver)
                    .build();

            return AgentExecutorEx.builder()
                    .stateSerializer(stateSerializer)
                    .chatModel(chatModel)
                    .defaultSystem("""
                        You are a commit assistant.
                        
                        run tool `git-list-files` to get list of staging files.
                        For each file:
                         - involve `agent-commit`  to get the commit message for such file.
                         - repeat for the next file
                        
                        """)
                    .tool( AgentCommitAssistant.subAgent(
                            chatModel,
                            compileConfig,
                            SkillPath.of( Paths.get("spring-ai/spring-ai-agent/src/test/resources/skills/agent-commit/") ),
                            logConsumer) )
                    .tools( AgentCommitAssistant.Tools.get().stream()
                                .filter( t -> t.getToolDefinition().name().equals("git-list-files"))
                                .toList())
                    .addNodeHook( logHook.asBeforeCall() )
                    .addNodeHook( logHook.asAfterCall() )
                    .build(compileConfig);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}