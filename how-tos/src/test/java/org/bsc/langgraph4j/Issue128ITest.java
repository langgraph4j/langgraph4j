package org.bsc.langgraph4j;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Issue128ITest {

    class DummyTool {

        @Tool("Use it to return a useless dummy data")
        String dummy() {
            return "are you crazy ?";
        }
    }

    enum AiModel {


        OPENAI( (model) -> OpenAiChatModel.builder()
                .apiKey( System.getenv("OPENAI_API_KEY") )
                .modelName( model )
                .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build() ),
        OLLAMA( ( model ) -> OllamaChatModel.builder()
                .modelName(model)
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.5)
                .build() )
        ;

        private final Function<String,ChatModel> modelFactory;

        public ChatModel model( String model ) {
            return modelFactory.apply(model);
        }

        AiModel(   Function<String,ChatModel> modelFactory ) {
            this.modelFactory = modelFactory;
        }
    }

    @Test
    public void agentExecutorTest() throws Exception {

        var agent = AgentExecutor.builder()
                .chatModel(AiModel.OLLAMA.model("qwen3"))
                .toolsFromObject( new DummyTool() )
                .build()
                .compile();

        var raw_text = """
                Translate "Hello, my master." into cat language
                """;

        var prompt1 = UserMessage.from(raw_text);

        var result = agent.invoke( Map.of( "messages",prompt1 ));

        System.out.println( result.orElseThrow() );

        var prompt_template = PromptTemplate.from(
                """
                <|begin_of_text|><|start_header_id|>user<|end_header_id|>
                {{raw_text}} <|eot_id|><|start_header_id|>assistant<|end_header_id|>
                """);
        var prompt2 = prompt_template.apply( Map.of("raw_text", raw_text)).toUserMessage();

        var result2 = agent.invoke( Map.of( "messages",prompt2 ));

        System.out.println( result2.orElseThrow() );


    }

}
