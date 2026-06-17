package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public interface CustomSubAgent extends SubAgent{

    class Builder extends ReactAgentBuilderEx<Builder, AgentExecutorEx.State> {
        private String name;
        private String description;

        public Builder name( String name ) {
            this.name = name;
            return this;
        }
        public Builder description( String description ) {
            this.description = description;
            return this;
        }


        public SubAgent build(CompiledGraph<AgentExecutorEx.State> agent) throws Exception {
            requireNonNull(name, "name cannot be null!");
            requireNonNull(description, "description cannot be null!");

            final var function =  FunctionToolCallback.<Input, String>builder(name, (input, context ) -> {

                        final var graphInput = GraphInput.args( Map.of( "messages", UserMessage.builder().text(input.context()).build() ));

                        return agent.invoke(graphInput, RunnableConfig.builder().build())
                                .flatMap(MessagesState::lastMessage)
                                .map( Message::getText )
                                .orElseThrow(() -> new IllegalStateException("no output message found!"));
                    })
                    .description(description)
                    .inputType(Input.class)
                    .build();

            return new SubAgentImpl(function, agent);

        }

    }

}
