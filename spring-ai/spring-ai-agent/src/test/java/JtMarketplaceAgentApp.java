//DEPS org.bsc.langgraph4j:langgraph4j-springai-agentexecutor:1.9.0-beta1
//DEPS org.bsc.langgraph4j:langgraph4j-bom:1.9.0-beta1@pom
//DEPS org.bsc.langgraph4j:langgraph4j-core
//DEPS org.bsc.langgraph4j:langgraph4j-javelit
//DEPS org.springframework.ai:spring-ai-bom:1.1.3@pom
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
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.agent.skill.SkillPath;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.spring.ai.agent.AgentMarketplace;
import org.bsc.langgraph4j.spring.ai.agent.AgentPayment;
import org.bsc.langgraph4j.spring.ai.agent.AiModel;
import org.bsc.langgraph4j.spring.ai.agent.SkilledReactSubAgent;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Content;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Javelite App
 *
 *
 */
public class JtMarketplaceAgentApp {

    final JtSessionValue<CompiledGraph<AgentExecutorEx.State>> agentSession = new JtSessionValue<>("agent");

    public static void main(String[] args) {

        var app = new JtMarketplaceAgentApp();

        app.view();
    }

    public void view() {

        Jt.title("Marketplace Agent").use();
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

            var input = Jt.textArea("input")
                    .value("""
                    search for product 'X'
                    
                    if found proceed to payment using  IBAN US82WEST1234567890123456
                    """)
                    .placeholder("input")
                    .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
                    .use();
            if (Jt.button("Start").disabled(input.isEmpty()).use()) {

                final var spinner = JtSpinner.builder()
                        .message("**starting the agent** ....")
                        .use();

                final var outputComponent = Jt.expander("Workflow Steps").use();

                try {
                    final var startTime = Instant.now();

                    final var agent = agentSession.computeIfAbsent( key -> buildAgent(chatModel, true) );

                    final var runnableConfig = RunnableConfig.builder()
                            .threadId("agent-marketplace-001")
                            .build();

                    final var generator = agent.stream(
                            GraphInput.args( Map.of("messages", new UserMessage(input)) ),
                            runnableConfig);

                    Jt.info("starting agentic workflow").use(outputComponent);

                    for (var step : generator) {

                        Jt.info("""
                                #### %s
                                ```
                                %s
                                ```
                                """.formatted(
                                        step.node(),
                                        step.state().messages().stream()
                                            .map(Object::toString)
                                            .collect(Collectors.joining("\n\n"))))
                                .use(outputComponent);

                        if (step.isEND()) {
                            final var response = step.state().lastMessage()
                                    .map(Content::getText)
                                    .orElse("No response found");

                            final var elapsedTime = Duration.between(startTime, Instant.now());

                            Jt.success("finished in %ds%n%n%s".formatted(elapsedTime.toSeconds(), response))
                                    .use(spinner);
                        }
                    }


                } catch (Throwable ex) {
                    Jt.error(ex.getMessage()).use(spinner);
                }
            }
        }
    }


    public CompiledGraph<AgentExecutorEx.State> buildAgent(ChatModel chatModel, boolean streaming)  {

        try {
            final var stateSerializer = new SpringAIJacksonStateSerializer<>(AgentExecutorEx.State::new);

            final var rootPath = Paths.get("target", "checkpoint");

            final var saver = new FileSystemSaver(rootPath, stateSerializer);

            var compileConfig = CompileConfig.builder()
                    .checkpointSaver(saver)
                    .build();

            final var subAgentMarketplace = SkilledReactSubAgent.builder()
                    .chatModel(chatModel)
                    .toolsFromObject(AgentMarketplace.tools())
                    .build( SkillPath.of(Paths.get("spring-ai/spring-ai-agent/src/test/resources/skills/agent-marketplace/")),
                            compileConfig);

            final var subAgentPayment = SkilledReactSubAgent.builder()
                    .chatModel(chatModel)
                    .toolsFromObject(AgentPayment.tools())
                    .build( SkillPath.of(Paths.get("spring-ai/spring-ai-agent/src/test/resources/skills/agent-payment/")),
                            compileConfig );

            final var agent = AgentExecutorEx.builder()
                    .chatModel(chatModel)
                    .tool(subAgentMarketplace)
                    .tool(subAgentPayment)
                    .build();


            return agent.compile(compileConfig);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}