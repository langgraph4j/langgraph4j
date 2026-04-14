//DEPS org.bsc.langgraph4j:langgraph4j-springai-agentexecutor:1.8.11
//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.8.11
//DEPS net.sourceforge.plantuml:plantuml-mit:1.2025.10
//DEPS org.springframework.ai:spring-ai-bom:1.1.0@pom
//DEPS org.springframework.ai:spring-ai-client-chat
//DEPS org.springframework.ai:spring-ai-openai
//DEPS org.springframework.ai:spring-ai-ollama
//DEPS org.springframework.ai:spring-ai-google-genai
//DEPS org.springframework.ai:spring-ai-azure-openai

//SOURCES org/bsc/langgraph4j/spring/ai/agentexecutor/AiModel.java
//SOURCES org/bsc/langgraph4j/spring/ai/agentexecutor/TestTools.java
//SOURCES org/bsc/langgraph4j/spring/ai/agentexecutor/gemini/TestTools4Gemini.java

import io.javelit.core.Jt;
import io.javelit.core.JtComponent;
import org.bsc.javelit.JtPlantUMLImage;
import org.bsc.javelit.JtSelectAiModel;

import org.bsc.javelit.JtSpinner;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AiModel;
import org.bsc.langgraph4j.spring.ai.agentexecutor.TestTools;
import org.bsc.langgraph4j.spring.ai.agentexecutor.gemini.TestTools4Gemini;
import org.bsc.langgraph4j.spring.ai.serializer.std.SpringAIStateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Content;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;

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
        var streaming = Jt.toggle("Streaming output").value(false).use();

        Jt.divider().use();

        if (modelOptional.isEmpty()) return;

        var model = modelOptional.get();

        var chatModel = switch( model.provider() ) {
            case OPENAI -> AiModel.OPENAI.chatModel( model.name(), model.attributes() );
            case GITHUB -> AiModel.GITHUB_MODEL.chatModel( model.name(), model.attributes() );
            case VERTEX -> AiModel.GEMINI.chatModel( model.name(), model.attributes() );
            case OLLAMA -> AiModel.OLLAMA.chatModel( model.name(), model.attributes() );
        };

        try {
            var agent = buildAgent(chatModel, streaming);

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

                try {
                    final var startTime = Instant.now();

                    var outputComponent = Jt.expander("Workflow Steps").use();

                    var input = GraphInput.args(Map.of("messages", new UserMessage(userMessage)));;

                    var runnableConfig = RunnableConfig.builder()
                            .threadId("test-01")
                            .build();

                    var generator = agent.stream(input, runnableConfig);


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
                            .map(Content::getText)
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

    public CompiledGraph<AgentExecutor.State> buildAgent(ChatModel chatModel, boolean streaming) throws Exception {
        var saver = new MemorySaver();

        var stateSerializer = new SpringAIStateSerializer<>(AgentExecutor.State::new);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var agentBuilder = AgentExecutor.builder()
                //.stateSerializer(stateSerializer)
                .chatModel(chatModel)
                .streaming(true);

        // FIX for GEMINI MODEL
        if (chatModel instanceof GoogleGenAiChatModel) {
            agentBuilder.toolsFromObject(new TestTools4Gemini());
        } else {
            agentBuilder.toolsFromObject(new TestTools());
        }

        return agentBuilder
                .build()
                .compile(compileConfig);

    }

}
