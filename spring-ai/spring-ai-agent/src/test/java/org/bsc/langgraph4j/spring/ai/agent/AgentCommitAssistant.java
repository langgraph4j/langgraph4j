package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.agent.skill.SkillSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public interface AgentCommitAssistant {

    class Tools {

        private CompletableFuture<String> readStdout(Process process) {
            return CompletableFuture.supplyAsync(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.lines()
                            .map(String::stripTrailing)
                            .filter(line -> !line.isBlank())
                            .collect(Collectors.joining("\n"));                          // Java 16+
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

            });
        }

        private CompletableFuture<String> runGit(String... args) {
            var cwd = ofNullable(System.getenv("CWD"))
                    .orElseGet(() -> System.getProperty("user.dir"));

            var command = new ArrayList<String>();
            command.add("git");
            command.addAll(Arrays.asList(args));

            final var processBuilder = new ProcessBuilder(command)
                    .directory(Path.of(cwd).toFile())
                    .redirectErrorStream(true);

            try {

                final var process = processBuilder.start();

                return process.onExit().thenCombine(readStdout(process), (p, stdout) -> {

                    final var exitCode = p.exitValue();
                    if (exitCode != 0) {
                        throw new CompletionException(new IOException("Git command failed with exit code %d. stderr: %s".formatted(exitCode, stdout)));
                    }

                    return stdout;
                });

            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Tool(name="git-diff", description = "get the git diff of a file")
        public String diff(@ToolParam(description = "the commit file path") String filename) {
            return runGit("diff", "--staged", "--", filename).join();
        }

        @Tool( name="git-list-files", description = "List files by git status; when staged=true, list staged files")
        public List<String> listFiles() {

            return runGit("diff", "--cached", "--name-only")
                    .thenApply(output ->
                            output.lines()
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .toList())
                    .join();
        }

        @Tool( name="git-commit", description = "perform a git commit for a specific file using the provided message")
        public String commit(
                @ToolParam(description = "the commit file path") String filename,
                @ToolParam(description = "the commit message") String message
        )  {

            return "file '%s' commited".formatted(filename);
            /*
            return runGit( "commit", "-m", message, filename)
                    .thenApply(output -> output )
                    .join();

             */

        }

        private static final ToolCallback[] toolCallbacks = ToolCallbacks.from( new Tools() );

        public static List<ToolCallback> get() {
            return List.of( toolCallbacks );
        }
    }

    static SkilledReactSubAgent subAgent(ChatModel chatModel, CompileConfig compileConfig, SkillSource skillSource) throws Exception {
        return SkilledReactSubAgent.builder()
                .chatModel(chatModel)
                .streaming(true)
                .tools( Tools.get() )
                .approvalOn( "git-commit", (nodeId, state ) -> {

                    final var toolCall = state.getToolCallByNameFromLastMessage( "git-commit" )
                            .orElseThrow();

                    return InterruptionMetadata.builder(nodeId, state)
                            .addMetadata( "TOOL_CALLS", toolCall)
                            .build();
                })
                .build( compileConfig, skillSource );

    }
}
