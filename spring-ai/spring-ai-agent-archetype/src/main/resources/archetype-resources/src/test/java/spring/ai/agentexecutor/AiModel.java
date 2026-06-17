#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agentexecutor;

import com.google.genai.Client;
import com.openai.client.OpenAIClientImpl;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Map;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

public enum AiModel {

    OPENAI( (model, extra) ->
            OpenAiChatModel.builder()
                    .openAiClient( new OpenAIClientImpl(com.openai.core.ClientOptions.builder()
                            .apiKey( extraAttribute( extra, "OPENAI_API_KEY" ) )
                            .build()))
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .logprobs(false)
                            //.temperature(0.0)
                            .build())
                    .build()),
    OLLAMA( (model,extra) ->
            OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl("http://localhost:11434")
                            .build())
                    .options(OllamaChatOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .build())
                    .build()),
    GITHUB_MODEL( (model, extra) ->
            OpenAiChatModel.builder()
                    .openAiClient( new OpenAIClientImpl(com.openai.core.ClientOptions.builder()
                            .baseUrl("https://models.github.ai/inference")
                            .apiKey( extraAttribute( extra,"GITHUB_MODELS_TOKEN") )
                            .build()))
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .logprobs(false)
                            .temperature(0.1)
                            .build())
                    .build()),
    GEMINI( (model,extra) ->
            GoogleGenAiChatModel.builder()
                    .genAiClient( Client.builder()
                            .vertexAI(true)
                            .project( extraAttribute(extra,"GOOGLE_CLOUD_PROJECT") )
                            .location( extraAttribute(extra,"GOOGLE_CLOUD_LOCATION") )
                            .build())
                    .options(GoogleGenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .build())
                    .build())
    ;

    private final BiFunction<String, Map<String,String>, ChatModel> model;

    private static  String extraAttribute(Map<String,String> extraAttributes, String key  ) {
        if( extraAttributes == null ) extraAttributes = Map.of();
        var result = extraAttributes.getOrDefault(
                requireNonNull(key,"key cannot be null"),
                System.getProperty(key, System.getenv(key)));
        return requireNonNull( result, "Value of attribute '%s' is null".formatted(key) );
    }

    public ChatModel chatModel(String model ) {
        return this.model.apply(model, Map.of());
    }
    public ChatModel chatModel(String model, Map<String,String> extraAttributes ) {
        return this.model.apply(model, extraAttributes);
    }

    AiModel( BiFunction<String, Map<String,String>, ChatModel> model ) {
        this.model = model;
    }

}
