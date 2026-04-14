//DEPS org.bsc.langgraph4j:langgraph4j-agent-executor:1.8.12
//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.8.12
//DEPS net.sourceforge.plantuml:plantuml-mit:1.2025.10
//DEPS dev.langchain4j:langchain4j-bom:1.9.1@pom
//DEPS dev.langchain4j:langchain4j-github-models
//DEPS dev.langchain4j:langchain4j-open-ai
//DEPS dev.langchain4j:langchain4j-ollama
//DEPS dev.langchain4j:langchain4j-azure-open-ai
//DEPS dev.langchain4j:langchain4j-vertex-ai-gemini


//SOURCES org/bsc/langgraph4j/agentexecutor/AiModel.java
//SOURCES org/bsc/langgraph4j/agentexecutor/TestTools.java

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.javelit.core.Jt;
import io.javelit.core.JtComponent;
import org.bsc.javelit.JtPlantUMLImage;
import org.bsc.javelit.JtSelectAiModel;
import org.bsc.javelit.JtSpinner;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.agentexecutor.AiModel;
import org.bsc.langgraph4j.agentexecutor.TestTools;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

public class JtAgentExecutorApp {


    public static void main(String[] args) {

        var app = new JtAgentExecutorApp();

        app.view();
    }

    public void view() {
        Jt.title("LangGraph4J React Agent").use();
        Jt.markdown("### Powered by LangGraph4j and SpringAI").use();

        var modelOptional = JtSelectAiModel.get();
        var streaming = Jt.toggle("Streaming output")
                .disabled(true)
                .value(false)
                .use();

        Jt.divider().use();

        if (modelOptional.isEmpty()) return;

        var model = modelOptional.get();

        var chatModel = switch( model.provider() ) {
            case OPENAI -> AiModel.OPENAI.chatModel( model.name(), model.attributes() );
            case GITHUB -> AiModel.GITHUB.chatModel( model.name(), model.attributes() );
            case VERTEX -> AiModel.GEMINI.chatModel( model.name(), model.attributes() );
            case OLLAMA -> AiModel.OLLAMA.chatModel( model.name(), model.attributes() );
        };

        try {
            var agent = buildAgent(chatModel);

            if (Jt.toggle("Show PlantUML Diagram").value(false).use()) {
                JtPlantUMLImage.build(agent.getGraph(GraphRepresentation.Type.PLANTUML,
                                "ReAct Agent",
                                false))
                        .ifPresent(cb -> {
                            cb.use();
                            Jt.divider().use();
                        });
            }

            var userMessage = Jt.textArea("user message:")
                    .placeholder("user message")
                    .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
                    .use();

            var start = Jt.button("start agent")
                    .disabled(userMessage.isBlank())
                    .use();

            if (start) {

                var spinner = JtSpinner.builder()
                        .message("**starting the agent** ....")
                        .use();

                var outputComponent = Jt.expander("Workflow Steps").use();

                var input = Map.<String, Object>of("messages", new UserMessage(userMessage));

                var runnableConfig = RunnableConfig.builder()
                        .threadId("test-01")
                        .build();

                var generator = agent.stream(input, runnableConfig);


                try {

                    final var startTime = Instant.now();

                    var output = generator.stream()
                            .peek(s -> {
                                if (s instanceof StreamingOutput<?> out) {
                                    var prev = Jt.sessionState().getString("streaming", "");

                                    if (!out.chunk().isEmpty()) {

                                        var partial = prev + out.chunk();

                                        Jt.markdown("""
                                                #### %s
                                                ```
                                                %s
                                                ```
                                                ***
                                                """.formatted(out.node(), partial)).use(outputComponent);

                                        Jt.sessionState().put("streaming", partial);
                                    }
                                } else {

                                    Jt.sessionState().remove("streaming");
                                    Jt.info("""
                                            #### %s
                                            ```
                                            %s
                                            ```
                                            """.formatted(s.node(),
                                            s.state().messages().stream()
                                                    .map(Object::toString)
                                                    .collect(Collectors.joining("\n\n")))
                                    ).use(outputComponent);
                                }
                            })
                            .reduce((a, b) -> b)
                            .orElseThrow();


                    var response = output.state().lastMessage()
                            .map(Object::toString)
                            .orElse("No response found");

                    final var elapsedTime = Duration.between(startTime, Instant.now());

                    Jt.success("finished in %ds%n%n%s".formatted(elapsedTime.toSeconds(), response))
                            .use(spinner);
                } catch (Exception e) {
                    Jt.error(e.getMessage()).use(spinner);
                }

            }
        } catch (Exception e) {
            Jt.error(e.getMessage()).use();
        }

    }

    public CompiledGraph<AgentExecutor.State> buildAgent(ChatModel chatModel) throws Exception {
        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        return AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(new TestTools())
                .build()
                .compile(compileConfig);

    }

}
