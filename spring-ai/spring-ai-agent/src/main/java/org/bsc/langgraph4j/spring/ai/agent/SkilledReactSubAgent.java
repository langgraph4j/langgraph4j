package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agent.skill.SkillParser;
import org.bsc.langgraph4j.agent.skill.SkillSource;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;

import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * A {@link ToolCallback} backed by a React-style sub-agent whose tool metadata and system
 * prompt are loaded from a skill source.
 * <p>
 * The skill front matter defines the exposed tool name, description, and optional allowed
 * tool list, while the Markdown body becomes the sub-agent system prompt.
 */
public interface SkilledReactSubAgent extends SubAgent {


    /**
     * Builder for creating a {@link SkilledReactSubAgent} from a skill definition and the
     * underlying React agent configuration.
     */
    class Builder extends ReactAgentBuilderEx<Builder, AgentExecutorEx.State> {

        /**
         * Builds a skill-backed React sub-agent from the provided skill source.
         * <p>
         * The skill front matter must define at least the {@code name} and
         * {@code description} properties. When {@code allowed-tools} is present, the current
         * tool set is filtered before compiling the sub-agent.
         *
         * @param skillSource the source used to load the skill markdown definition
         * @param compileConfig the compile configuration to use, or {@code null} to use defaults
         * @return a configured {@link SkilledReactSubAgent}
         * @throws Exception if the skill cannot be loaded or the agent cannot be built
         */
        public SubAgent build( SkillSource skillSource, CompileConfig compileConfig ) throws Exception {

            final var parser = SkillParser.of(skillSource.content());

            final var frontMatter = parser.getFrontMatter();

            final var name =  frontMatter.getString("name")
                    .orElseThrow( () -> new IllegalStateException("'name' property not found!"));
            final var description =  frontMatter.getString("description")
                    .orElseThrow( () -> new IllegalStateException("'description' property not found!"));

            // select only allowed tools if defined
            frontMatter.getStringList("allowed-tools").ifPresent( allowedTools -> {
                tools.removeIf( tool -> allowedTools.stream().
                        noneMatch(allowedTool ->
                                allowedTool.equalsIgnoreCase(tool.getToolDefinition().name())));
            });

            final var agent = new AgentExecutorEx.Builder( this )
                    .defaultSystem( parser.getContent() )
                    .build()
                    .compile( ofNullable(compileConfig)
                            .orElseGet( () -> CompileConfig.builder().build() ));

            final var function =  FunctionToolCallback.<Input, String>builder(name, ( input, context ) -> {

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

    /**
     * Creates a new builder for configuring a skill-backed React sub-agent.
     *
     * @return a new {@link Builder} instance
     */
    static Builder builder() {
        return new Builder();
    }
}
