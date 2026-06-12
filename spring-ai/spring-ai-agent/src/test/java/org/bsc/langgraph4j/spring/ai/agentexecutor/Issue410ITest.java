package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.support.ToolCallbacks;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class Issue410ITest {


    @Test
    void doNotExecuteToolsAutomaticallyUsingChatModel() throws Exception {

        final var toolCallingManager = DefaultToolCallingManager.builder()
                                        .build();


        final var options$ = OllamaChatOptions.builder()
                .model("qwen3.5")
                .temperature(0.7)
                .toolCallbacks()
                .build();

        final var chatModel = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl("http://localhost:11434")
                        .build())
                .options( options$ )
                .build();

       final var finalOptions =  chatModel.getOptions().mutate()
               .toolCallbacks(ToolCallbacks.from(new TestTools()))
               .build();

        var  prompt = Prompt.builder()
                    .messages(UserMessage.builder()
                                .text( "return current number of system thread allocated by application" )
                                .build())
                .chatOptions(finalOptions)
                .build();

        ChatResponse response = chatModel.call( prompt );

        while (response.hasToolCalls()) {
            ToolExecutionResult toolResult =
                    toolCallingManager.executeToolCalls(prompt, response);

            final var conversationHistory = toolResult.conversationHistory();

            prompt = new Prompt(conversationHistory);
            response = chatModel.call(prompt);
        }

        String finalAnswer = response.getResult().getOutput().getText();

        System.out.println(finalAnswer);
    }

    @Test
    void doNotExecuteToolsAutomaticallyUsingChatClient() throws Exception {

        final var toolCallingManager = DefaultToolCallingManager.builder()
                .build();

        final var chatModel = AiModel.OLLAMA.chatModel("qwen3.5");

        final var chatClient = ChatClient.builder( chatModel )
                .defaultTools(new TestTools())
                .build();

        var  prompt = Prompt.builder()
                .messages(UserMessage.builder()
                        .text( "return current number of system thread allocated by application" )
                        .build())
                .build();

        ChatResponse response = chatClient.prompt( prompt )
                .toolContext( Map.of( "execTest", new ToolContext(Map.of()),
                                    "threadCount", new ToolContext(Map.of())))
                .call()
                .chatResponse();

        assertFalse( response.hasToolCalls() );
        while (response.hasToolCalls()) {
            ToolExecutionResult toolResult =
                    toolCallingManager.executeToolCalls(prompt, response);

            final var conversationHistory = toolResult.conversationHistory();

            prompt = new Prompt(conversationHistory);
            response = chatClient.prompt(prompt).call().chatResponse();
        }

        String finalAnswer = response.getResult().getOutput().getText();

        System.out.println(finalAnswer);
    }

}
