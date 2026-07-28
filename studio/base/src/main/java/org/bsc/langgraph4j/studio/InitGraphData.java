package org.bsc.langgraph4j.studio;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Initialization data for the graph.
 *
 * @param id the graph identifier
 * @param title the title of the graph.
 * @param diagram the graph content.
 * @param args the arguments for the graph.
 * @param threads the thread entries.
 */
public record InitGraphData(
        String id,
        String title,
        String diagram,
        List<ArgumentMetadata> args,
        List<ThreadEntry> threads) {

    public InitGraphData {
        requireNonNull( id, "id cannot be null");
    }
    public InitGraphData(String id, String title, String diagram, List<ArgumentMetadata> args) {
        this(id, title, diagram, args, List.of(new ThreadEntry("default", List.of())));
    }

    public InitGraphData(String id, String title, String diagram) {
        this(id, title, diagram, List.of());
    }
}
