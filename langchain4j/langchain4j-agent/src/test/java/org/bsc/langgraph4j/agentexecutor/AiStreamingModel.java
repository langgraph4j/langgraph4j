package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.Map;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

public enum AiStreamingModel {

    OPENAI( (model, extra) ->
            OpenAiStreamingChatModel.builder()
                .apiKey( extraAttribute( extra, "OPENAI_API_KEY" ) )
                //.modelName( "gpt-4o-mini" )
                .modelName(model)
                .logResponses(true)
                .temperature(0.0)
                .build() ),
    GITHUB( (model, extra) ->
            OpenAiStreamingChatModel.builder()
                    .baseUrl("https://models.github.ai/inference")
                    .apiKey( extraAttribute( extra, "GITHUB_MODELS_TOKEN" ) )
                    //.modelName( "gpt-4o-mini" )
                    .modelName(model)
                    .logResponses(true)
                    .temperature(0.0)
                    .build() ),
    OLLAMA( (model, extra) ->
            OllamaStreamingChatModel.builder()
                .modelName( model )
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .temperature(0.0)
                .build() )
    ;

    private final BiFunction<String, Map<String,String>, StreamingChatModel> model;

    private static  String extraAttribute(Map<String,String> extraAttributes, String key  ) {
        if( extraAttributes == null ) extraAttributes = Map.of();
        var result = extraAttributes.getOrDefault(
                requireNonNull(key,"key cannot be null"),
                System.getProperty(key, System.getenv(key)));
        return requireNonNull( result, "Value of attribute '%s' is null".formatted(key) );
    }

    public StreamingChatModel chatModel(String model ) {
        return this.model.apply(model, Map.of());
    }
    public StreamingChatModel chatModel(String model, Map<String,String> extraAttributes ) {
        return this.model.apply(model, extraAttributes);
    }

    AiStreamingModel(BiFunction<String, Map<String,String>, StreamingChatModel> model ) {
        this.model = model;
    }
}
