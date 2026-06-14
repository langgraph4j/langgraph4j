package org.bsc.langgraph4j.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class LC4jToolMapBuilder<T extends LC4jToolMapBuilder<T>> {
    private final Map<ToolSpecification, ToolExecutor> toolMap = new HashMap<>();
    private Skills skills;

    public Map<ToolSpecification, ToolExecutor> toolMap() {
        return Map.copyOf(toolMap);
    }

    public Optional<Skills> skills() { return ofNullable(skills);  }

    @SuppressWarnings("unchecked")
    protected T result() {
        return (T) this;
    }

    /**
     * Sets the tool specification for the graph builder.
     *
     * @param objectWithTools the tool specification
     * @return the updated GraphBuilder instance
     */
    public final T toolsFromObject(Object objectWithTools) {

        Class<?> clazz = requireNonNull(objectWithTools, "class cannot be null").getClass();

        List<Method> toolMethods = List.of();
        while( clazz != null  && clazz != Object.class ) {

            toolMethods = Stream.of( clazz.getDeclaredMethods() )
                    .filter( method -> method.isAnnotationPresent(Tool.class))
                    .toList();

            if( !toolMethods.isEmpty()  ) break;

            if( clazz.isSynthetic() ) {
                clazz = clazz.getSuperclass();
            }
        }

        toolMethods.forEach( method -> {
            final var toolExecutor = new DefaultToolExecutor(objectWithTools, method);
            toolMap.put( toolSpecificationFrom(method), toolExecutor );
        });

        return result();


    }

    public final T toolsFromObject(Object ...objectWithTools){

        Stream.of(objectWithTools).forEach(this::toolsFromObject);

        return result();
    }

    /**
     * Sets the tool specification with executor for the graph builder.
     *
     * @param spec    the tool specification
     * @param executor the tool executor
     * @return the updated builder instance
     */
    public final T tool(ToolSpecification spec, ToolExecutor executor) {
        toolMap.put(spec, executor);
        return result();
    }

    public final T tool(Map.Entry<ToolSpecification, ToolExecutor> entry) {
        toolMap.put(entry.getKey(), entry.getValue());
        return result();
    }

    /**
     * add tools published by the mcp client
     * @param mcpClient mcpClient instance
     * @return the updated builder instance
     */
    public final T tool( McpClient mcpClient ) {
        requireNonNull(mcpClient, "mcpClient cannot be null");

        for (var toolSpecification : mcpClient.listTools()) {
            tool(toolSpecification, (request, o) -> mcpClient.executeTool(request).resultText());
        }
        return result();
    }

    public final T skills(Skill... skills) {
        return skills( Arrays.asList(requireNonNull(skills, "skills cannot be null")));
    }

    public final T skills(Collection<? extends Skill> skills) {

        final var skillManager = Skills.from( requireNonNull(skills, "skills cannot be null"));

        final var toolProvider = skillManager.toolProvider();

        if( toolProvider.isDynamic() ) {
            throw new UnsupportedOperationException("Dynamic tool providers are not supported yet!");
        }
        final var result = toolProvider.provideTools(
                ToolProviderRequest.builder()
                        .invocationContext(InvocationContext.builder().build())
                        .userMessage( UserMessage.from(""))
                        .build() );
        result.aiServiceTools()
                .forEach( tool -> toolMap.put(tool.toolSpecification(), tool.toolExecutor()) );

        return result();
    }

}
