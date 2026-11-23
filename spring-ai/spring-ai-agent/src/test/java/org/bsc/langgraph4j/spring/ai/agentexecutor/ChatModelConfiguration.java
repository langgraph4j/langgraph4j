package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.langgraph4j.spring.ai.AIModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ChatModelConfiguration {

    @Bean
    @Profile("ollama")
    public ChatModel ollamaModel() {
        return AIModel.OLLAMA.chatModel("qwen3.5");
    }

    @Bean
    @Profile("openai")
    public ChatModel openaiModel() {
        return AIModel.OPENAI.chatModel("gpt-5-mini");
    }

    @Bean
    @Profile("gemini")
    public ChatModel geminiModel() {
        return AIModel.GEMINI.chatModel("gemini-2.5-pro");
    }

    @Bean
    @Profile("github-models")
    public ChatModel githubModel() {
        return AIModel.GITHUB_MODEL.chatModel("gpt-4o-mini");
    }

}
