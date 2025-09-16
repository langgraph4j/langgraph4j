#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ChatModelConfiguration {

    @Bean
    @Profile("ollama")
    public ChatModel ollamaModel() {
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
                .defaultOptions(OllamaOptions.builder()
                        .model("qwen2.5:7b")
                        .temperature(0.1)
                        .build())
                .build();
    }

    @Bean
    @Profile("openai")
    public ChatModel openaiModel() {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        //.baseUrl("https://api.openai.com")
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .logprobs(false)
                        .temperature(0.1)
                        .build())
                .build();

    }

    @Bean
    @Profile("gemini")
    public ChatModel geminiModel() {
        return VertexAiGeminiChatModel.builder()
                .vertexAI( new VertexAI.Builder()
                        .setProjectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
                        .setLocation(System.getenv("GOOGLE_CLOUD_LOCATION"))
                        .setTransport(Transport.REST)
                        .build())
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model("gemini-2.5-pro")
                        .temperature(0.0)
                        .build())
                .build();

    }

    @Bean
    @Profile("github-models")
    public ChatModel githubModel() {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl("https://models.github.ai/inference") // GITHUB MODELS
                        .apiKey(System.getenv("GITHUB_MODELS_TOKEN"))
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .logprobs(false)
                        .temperature(0.1)
                        .build())
                .build();

    }

}
