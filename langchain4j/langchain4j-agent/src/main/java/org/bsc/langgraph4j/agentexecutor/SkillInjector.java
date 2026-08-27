package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skill;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agent.ConversationContextPolicy;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.utils.TypeRef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Tool-call–triggered dynamic skills: activate ids on successful tool results
 * (via execute-tools edge wrap + {@link Command#update()}), materialize bodies
 * through {@link ConversationContextPolicy} (never written into Graph State).
 */
public final class SkillInjector {

    private static final TypeRef<List<ToolExecutionResultMessage>> TOOL_RESULTS =
            new TypeRef<>() {};

    private final ToolSkillResolver resolver;
    private final Function<String, String> skillBody;

    private SkillInjector(ToolSkillResolver resolver, Function<String, String> skillBody) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.skillBody = Objects.requireNonNull(skillBody, "skillBody");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Wrap hook for {@link AgentExecutor.Builder#addExecuteToolsHook}: after tools run,
     * merge successful tool → skill ids into {@link AgentExecutor.State#ACTIVE_SKILLS}.
     */
    public EdgeHook.WrapCall<AgentExecutor.State> executeToolsHook() {
        return (sourceId, state, config, action) ->
                action.apply(state, config).thenApply(command -> activate(state, command));
    }

    /**
     * Policy that prepends activated skill bodies to the model-bound message view.
     * Does not mutate graph state.
     */
    public ConversationContextPolicy<ChatMessage> asConversationContextPolicy() {
        return asConversationContextPolicy(null);
    }

    /**
     * Decorates an existing policy: filter first, then prepend skill bodies.
     */
    public ConversationContextPolicy<ChatMessage> asConversationContextPolicy(
            ConversationContextPolicy<ChatMessage> existing) {
        return new ConversationContextPolicy<>() {
            @Override
            public <S extends MessagesState<ChatMessage>> List<ChatMessage> filter(
                    S state, RunnableConfig config) {
                List<ChatMessage> base = existing != null
                        ? existing.filter(state, config)
                        : new ArrayList<>(state.messages());
                List<String> ids = state instanceof AgentExecutor.State agentState
                        ? agentState.activeSkills()
                        : state.<List<String>>value(AgentExecutor.State.ACTIVE_SKILLS).orElseGet(List::of);
                if (ids.isEmpty()) {
                    return base;
                }
                var view = new ArrayList<ChatMessage>(ids.size() + base.size());
                for (String id : ids) {
                    var body = skillBody.apply(id);
                    if (body != null && !body.isBlank()) {
                        view.add(SystemMessage.from(body));
                    }
                }
                view.addAll(base);
                return view;
            }
        };
    }

    Command activate(AgentExecutor.State state, Command command) {
        var successfulTools = successfulToolNames(command);
        if (successfulTools.isEmpty()) {
            return command;
        }
        var merged = new LinkedHashSet<>(state.activeSkills());
        for (String toolName : successfulTools) {
            merged.addAll(resolver.resolve(toolName));
        }
        if (merged.equals(new LinkedHashSet<>(state.activeSkills()))) {
            return command;
        }
        return command.withMergedUpdate(Map.of(
                AgentExecutor.State.ACTIVE_SKILLS, new ArrayList<>(merged)));
    }

    static List<String> successfulToolNames(Command command) {
        var raw = command.update().get("messages");
        if (raw == null) {
            return List.of();
        }
        var messages = TOOL_RESULTS.cast(raw).orElse(null);
        if (messages == null) {
            return List.of();
        }
        var names = new ArrayList<String>();
        for (ToolExecutionResultMessage msg : messages) {
            if (!Boolean.TRUE.equals(msg.isError())) {
                names.add(msg.toolName());
            }
        }
        return names;
    }

    private static Function<String, String> bodyIndexFromSkills(Collection<? extends Skill> skills) {
        var byName = new LinkedHashMap<String, String>();
        for (Skill skill : skills) {
            byName.put(skill.name(), skill.content());
        }
        return id -> byName.getOrDefault(id, "");
    }

    public static final class Builder {
        private ToolSkillResolver resolver = toolName -> List.of();
        private Function<String, String> skillBody = id -> "";

        public Builder resolver(ToolSkillResolver resolver) {
            this.resolver = resolver;
            return this;
        }

        /**
         * Custom body loader by skill id (overrides {@link #skills} if set after).
         */
        public Builder skillBody(Function<String, String> skillBody) {
            this.skillBody = skillBody;
            return this;
        }

        /**
         * Index skill bodies from LC4j {@link Skill}s ({@link Skill#name()} → {@link Skill#content()}).
         * Compatible with {@link ClassPathSkillLoader} / {@link FileSystemSkillLoader} results.
         */
        public Builder skills(Collection<? extends Skill> skills) {
            this.skillBody = bodyIndexFromSkills(
                    Objects.requireNonNull(skills, "skills"));
            return this;
        }

        public Builder skills(Skill... skills) {
            return skills(List.of(Objects.requireNonNull(skills, "skills")));
        }

        /**
         * Load skills from the classpath (e.g. {@code "skills"} → {@code classpath:skills/.../SKILL.md}).
         */
        public Builder skillsFromClassPath(String resourceRoot) {
            return skills(ClassPathSkillLoader.loadSkills(
                    Objects.requireNonNull(resourceRoot, "resourceRoot")));
        }

        /**
         * Load skills from a filesystem directory.
         */
        public Builder skillsFromPath(Path directory) {
            return skills(FileSystemSkillLoader.loadSkills(
                    Objects.requireNonNull(directory, "directory")));
        }

        public SkillInjector build() {
            return new SkillInjector(resolver, skillBody);
        }
    }
}
