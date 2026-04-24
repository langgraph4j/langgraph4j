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
import java.util.function.Supplier;

public class Issue128ITest {

    class DummyTool {

        @Tool("Use it to return a useless dummy data")
        String dummy() {
            return "are you crazy ?";
        }
    }

    enum AiModel {


        OPENAI_GPT_4O_MINI( () -> OpenAiChatModel.builder()
                .apiKey( System.getenv("OPENAI_API_KEY") )
                .modelName( "gpt-4o-mini" )
                .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build() ),
        OLLAMA_LLAMA3_1_8B( () -> OllamaChatModel.builder()
                .modelName( "llama3.1" )
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.5)
                .build() ),
        OLLAMA_QWEN2_5_7B( () -> OllamaChatModel.builder()
                .modelName( "qwen2.5:7b" )
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build() )
        ;

        private final Supplier<ChatModel> modelSupplier;

        public ChatModel model() {
            return modelSupplier.get();
        }

        AiModel(  Supplier<ChatModel> modelSupplier ) {
            this.modelSupplier = modelSupplier;
        }
    }

    @Test
    public void agentExecutorTest() throws Exception {

        var agent = AgentExecutor.builder()
                .chatModel(AiModel.OLLAMA_LLAMA3_1_8B.model())
                .toolsFromObject( new DummyTool() )
                .build()
                .compile();

        var raw_text = """
                Translate "Hello, my master." into cat language
                """;

        var prompt1 = UserMessage.from(raw_text);

        var result = agent.invoke( GraphInput.args(Map.of( "messages",prompt1 )), RunnableConfig.empty() );

        System.out.println( result.orElseThrow() );

        var prompt_template = PromptTemplate.from(
                """
                <|begin_of_text|><|start_header_id|>user<|end_header_id|>
                {{raw_text}} <|eot_id|><|start_header_id|>assistant<|end_header_id|>
                """);
        var prompt2 = prompt_template.apply( Map.of("raw_text", raw_text)).toUserMessage();

        var result2 = agent.invoke( GraphInput.args(Map.of( "messages",prompt2 )), RunnableConfig.empty() );

        System.out.println( result2.orElseThrow() );


    }

}
