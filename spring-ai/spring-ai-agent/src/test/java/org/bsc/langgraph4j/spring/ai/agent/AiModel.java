package org.bsc.langgraph4j.spring.ai.agent;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
//import io.netty.channel.ChannelOption;
//import org.springframework.http.client.SimpleClientHttpRequestFactory;
//import org.springframework.http.client.reactive.ReactorClientHttpConnector;
//import org.springframework.web.client.RestClient;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.netty.http.client.HttpClient;
//import java.time.Duration;
import java.util.function.Function;

public enum AiModel {
    OPENAI( model ->
            OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .apiKey(System.getenv("OPENAI_API_KEY"))
                            .build())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model)
                            .logprobs(false)
                            .build())
                    .build()),
    OLLAMA( model ->
            OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl("http://localhost:11434")
                            //.restClientBuilder(RestClient.builder()
                            //        .requestFactory(ollamaRequestFactory()))
                            //.webClientBuilder(WebClient.builder()
                            //        .clientConnector(new ReactorClientHttpConnector(ollamaHttpClient())))
                            .build())
                    .defaultOptions(OllamaChatOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .build())
                    .build())
    ;

    private final Function<String,ChatModel> model;

    /*
    private static final Duration OLLAMA_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration OLLAMA_RESPONSE_TIMEOUT = Duration.ofMinutes(30);

    private static SimpleClientHttpRequestFactory ollamaRequestFactory() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(OLLAMA_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(OLLAMA_RESPONSE_TIMEOUT);
        return requestFactory;
    }

    private static HttpClient ollamaHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(OLLAMA_CONNECT_TIMEOUT.toMillis()))
                .responseTimeout(OLLAMA_RESPONSE_TIMEOUT);
    }
    */
    public ChatModel chatModel(String model ) {
        return this.model.apply(model);
    }

    AiModel(Function<String,ChatModel> model) {
        this.model = model;
    }

}
