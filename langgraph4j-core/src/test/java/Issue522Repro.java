//DEPS org.bsc.langgraph4j:langgraph4j-core:1.9-SNAPSHOT

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Self-contained reproduction for langgraph4j issue #522.
 *
 * Run with no arguments: it forks a child JVM per scenario and reports how long each child
 * takes to exit after its result is already on stdout.
 *
 *   javac -cp <runtime-classpath> Issue522Repro.java
 *   java  -cp .:<runtime-classpath> Issue522Repro        (use ; instead of : on Windows)
 *
 * Scenarios:
 *   default   one-node graph, consumed fully, no emitterExecutor   -> the case reported in #522
 *   managed   same, with a caller-provided executor                -> control
 *   abandoned a stream whose node is still working is dropped      -> exit blocked by a live thread
 */
public class Issue522Repro {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (var scenario : new String[] { "default", "managed", "abandoned" }) {
                for (int run = 1; run <= 3; run++) {
                    System.out.printf("%-10s run %d -> result-to-exit: %d ms%n",
                            scenario, run, measureChild(scenario));
                }
            }
            return;
        }
        switch (args[0]) {
            case "default" -> runGraph(false);
            case "managed" -> runGraph(true);
            case "abandoned" -> abandonStream();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /** one-node graph, fully consumed; prints RESULT once the value is available */
    private static void runGraph(boolean callerExecutor) throws Exception {
        var graph = new StateGraph<>(AgentState::new)
                .addNode("answer", node_async(state -> Map.of("answer", 42)))
                .addEdge(START, "answer")
                .addEdge("answer", END)
                .compile();

        var executor = callerExecutor ? Executors.newSingleThreadExecutor() : null;
        var config = executor == null
                ? RunnableConfig.empty()
                : RunnableConfig.builder().emitterExecutor(executor).build();
        try {
            int answer = graph.invoke(GraphInput.noArgs(), config).orElseThrow()
                    .<Integer>value("answer").orElseThrow();
            System.out.println("RESULT " + answer);
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    /** takes the first output of a graph whose node sleeps, then drops the stream */
    private static void abandonStream() throws Exception {
        var graph = new StateGraph<>(AgentState::new)
                .addNode("slow", node_async(state -> {
                    Thread.sleep(10_000);
                    return Map.of("done", true);
                }))
                .addEdge(START, "slow")
                .addEdge("slow", END)
                .compile();

        var first = graph.stream(GraphInput.noArgs(), RunnableConfig.empty())
                .stream()
                .findFirst()
                .orElseThrow();

        System.out.println("RESULT abandoned-after=" + first.node());
    }

    /** forks a child JVM and measures the interval between its RESULT line and its exit */
    private static long measureChild(String scenario) throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = List.of(java,
                "-cp", System.getProperty("java.class.path"),
                Issue522Repro.class.getName(),
                scenario);

        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        long resultAt = 0;
        try (var reader = process.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("RESULT")) {
                    resultAt = System.nanoTime();
                }
            }
        }
        process.waitFor();
        long exitAt = System.nanoTime();
        if (resultAt == 0) {
            throw new IllegalStateException("child never printed RESULT");
        }
        return (exitAt - resultAt) / 1_000_000;
    }
}