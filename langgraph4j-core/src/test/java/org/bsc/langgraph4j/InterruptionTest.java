package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.async.AsyncGeneratorQueue;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class InterruptionTest {

    static class StreamingGenerator extends AsyncGenerator.WithResult<StreamingOutput<MessagesState<String>>>{


        public StreamingGenerator(BlockingQueue<Data<StreamingOutput<MessagesState<String>>>> queue,
                                  MessagesState<String> startingState,
                                  String startingNode) {
            super(new AsyncGeneratorQueue.Generator<>(queue));

            queue.add(AsyncGenerator.Data.of( new StreamingOutput<>( "Test1", startingNode, startingState,null ) ) );
            queue.add(AsyncGenerator.Data.of( new StreamingOutput<>( "Test2", startingNode, startingState, null ) ) );
            queue.add(AsyncGenerator.Data.done( Map.of("messages", "Test1Test2") ));
        }

    }

    static class CustomAction implements AsyncNodeActionWithConfig<MessagesState<String>> {

        static class Interruptable extends CustomAction implements InterruptableAction<MessagesState<String>> {
            private final boolean interrupt;

            private Interruptable(Builder builder) {
                super(builder);
                interrupt = builder.interrupt;
            }

            private boolean isResume( RunnableConfig config ) {
                return config.metadata( "lc4j_resume" )
                        .map( Boolean.class::cast )
                        .orElse(false);
            }

            @Override
            public Optional<InterruptionMetadata<MessagesState<String>>> interrupt(String nodeId, MessagesState<String> state, RunnableConfig config) {
                if( interrupt && !isResume(config) ) {
                    assertEquals( nodeId, this.nodeId);
                    return Optional.of(InterruptionMetadata.builder(nodeId,state).build());
                }
                return Optional.empty();
            }

        }

        static class Builder {
            boolean interrupt;
            String nodeId;
            boolean streaming;

            public Builder nodeId( String nodeId ) {
                this.nodeId = nodeId;
                return this;
            }

            public Builder interrupt() {
                interrupt = true;
                return this;
            }

            public Builder streaming() {
                streaming = true;
                return this;
            }


            public CustomAction build() {
                return ( interrupt ) ?
                        new Interruptable(this) :
                        new CustomAction(this);
            }

        }

        public static Builder builder() {
            return new Builder();
        }

        final String nodeId;
        final boolean streaming;

        private CustomAction(Builder builder) {
            this.nodeId = requireNonNull(builder.nodeId, "nodeId cannot be null!");
            this.streaming = builder.streaming;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(MessagesState<String> state, RunnableConfig config) {
            if (streaming) {
                return completedFuture(Map.of("_streaming_messages", new StreamingGenerator(new LinkedBlockingQueue<>(),
                        state,
                        nodeId)));
            }
            return completedFuture(Map.of("messages", nodeId));
        }


    }

    private CustomAction _nodeAction(String id) {
        return CustomAction.builder().nodeId(id).build();
    }


    @Test
    public void interruptAfterEdgeEvaluation() throws Exception {
        var saver = new MemorySaver();

        var workflow = new MessagesStateGraph<String>()
                .addNode("A", _nodeAction("A"))
                .addNode("B", _nodeAction("B"))
                .addNode("C", _nodeAction("C"))
                .addNode("D", _nodeAction("D"))
                .addConditionalEdges("B",
                        edge_async(state -> {
                            var message = state.lastMessage().orElse( END );
                            return message.equals("B") ? "D" : message ;
                        }),
                        EdgeMappings.builder()
                                .to("A")
                                .to( "C" )
                                .to( "D" )
                                .toEND()
                                .build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("C", END)
                .addEdge("D", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter("B")
                        .build());

        var runnableConfig = RunnableConfig.builder().build();

        var results = workflow.stream(GraphInput.noArgs(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();

        assertIterableEquals(List.of(
                START,
                "A",
                "B"
        ), results);

        results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "D",
                END
        ), results );

        var snapshotForNodeB = workflow.getStateHistory(runnableConfig)
                                    .stream()
                                    .filter( s -> s.node().equals("B") )
                                    .findFirst()
                                    .orElseThrow();

        runnableConfig = workflow.updateState( snapshotForNodeB.config(), Map.of( "messages", "C"));
        results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "D",
                END
        ), results );
    }

    @Test
    public void interruptBeforeEdgeEvaluation() throws Exception {

        var saver = new MemorySaver();

        var workflow = new MessagesStateGraph<String>()
                .addNode("A", _nodeAction("A"))
                .addNode("B", _nodeAction("B"))
                .addNode("C", _nodeAction("C"))
                .addConditionalEdges("B",
                        edge_async(state ->
                                state.lastMessage().orElse( END ) ),
                        EdgeMappings.builder()
                                .to("A")
                                .to( "C" )
                                .toEND()
                                .build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("C", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter("B")
                        .interruptBeforeEdge(true)
                        .build());

        var runnableConfig = RunnableConfig.builder().build();

        var results = workflow.stream(GraphInput.noArgs(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();

        assertIterableEquals(List.of(
                START,
                "A",
                "B"
        ), results);

        // use GraphInput.resume(Map) instead
        // runnableConfig = workflow.updateState( runnableConfig, Map.of( "messages", "C"));
        results = workflow.stream(GraphInput.resume(Map.of( "messages", "C")), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "C",
                END
        ), results );
    }


    @Test
    public void dynamicInterruption() throws Exception {

        var saver = new MemorySaver();

        var workflow = new MessagesStateGraph<String>()
                .addNode("A", _nodeAction("A"))
                .addNode("B", _nodeAction("B"))
                .addNode("C", CustomAction.builder()
                                    .nodeId("C")
                                    .interrupt()
                                    .build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        var runnableConfig = RunnableConfig.builder().build();

        var results = workflow.stream(GraphInput.noArgs(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();

        assertIterableEquals(List.of(
                START,
                "A",
                "B"
        ), results);

        // use GraphInput.resume(Map) instead
        // runnableConfig = workflow.updateState( runnableConfig, Map.of( "messages", "C"));

        results = workflow.stream( GraphInput.resume(),
                                    runnableConfig.updateMetadata( Map.of("lc4j_resume", true) ) )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "C",
                END
        ), results );

    }


    /**
     * refer to issue <a href="https://github.com/langgraph4j/langgraph4j/issues/343">#309<a></a>
     */
    @Test
    void nodeOutputAfterStreaming() throws Exception {
        var saver = new MemorySaver();

        var workflow = new MessagesStateGraph<String>()
                .addNode("A", CustomAction.builder().nodeId("A").streaming().build() )
                .addNode("B", CustomAction.builder().nodeId("B").build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("B", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore("A", "B")
                        .build());

        var runnableConfig = RunnableConfig.builder().build();

        var results = workflow.stream(GraphInput.noArgs(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();

        assertIterableEquals(List.of(
                START
        ), results);

        results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "A",
                "A",
                "A"
        ), results );

        results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "B",
                END
        ), results );
    }

}

