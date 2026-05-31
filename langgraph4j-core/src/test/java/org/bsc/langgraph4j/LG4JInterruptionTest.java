package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.async.v5.BlockingQueueProcessor;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.bsc.langgraph4j.utils.TryConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.LG4JInterruptionTest.CustomAction.newAction;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class LG4JInterruptionTest {

    static class MyState extends MessagesState<String> {

        public MyState(Map<String, Object> initData) {
            super(initData);
        }
    }

    static class StreamingGenerator implements AsyncGenerator<StreamingOutput<MessagesState<String>>>, AsyncGenerator.HasResultValue {

        final AsyncGeneratorFlow.Generator<StreamingOutput<MessagesState<String>>> delegate;

        public StreamingGenerator(MessagesState<String> startingState,
                                  String startingNode) {

            final var processor = new BlockingQueueProcessor<StreamingOutput<MessagesState<String>>>();
            this.delegate = AsyncGeneratorFlow.builder()
                    .processor(processor)
                    .executor(Runnable::run)
                    .build();

            processor.dispatchAsync(AsyncGenerator.Data.of( new StreamingOutput<>( "Test1", startingNode, startingState,null ) ) );
            processor.dispatchAsync(AsyncGenerator.Data.of( new StreamingOutput<>( "Test2", startingNode, startingState, null ) ) );
            processor.dispatchAsync(AsyncGenerator.Data.done( Map.of("messages", "Test1Test2") ));
        }

        @Override
        public Data<StreamingOutput<MessagesState<String>>> next() {
            return delegate.next();
        }

        @Override
        public Executor executor() {
            return delegate.executor();
        }

        @Override
        public Optional<Object> resultValue() {
            return delegate.resultValue();
        }
    }

    static class CustomAction implements AsyncNodeActionWithConfig<MyState> {

        static class Interruptable extends CustomAction implements InterruptableAction<MyState> {
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
            public Optional<InterruptionMetadata<MyState>> interrupt(String nodeId, MyState state, RunnableConfig config) {
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

        static CustomAction newAction(String id) {
            return CustomAction.builder().nodeId(id).build();
        }

        final String nodeId;
        final boolean streaming;

        private CustomAction(Builder builder) {
            this.nodeId = requireNonNull(builder.nodeId, "nodeId cannot be null!");
            this.streaming = builder.streaming;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(MyState state, RunnableConfig config) {
            if (streaming) {
                return completedFuture(Map.of("_streaming_messages", new StreamingGenerator(state, nodeId)));
            }
            return completedFuture(Map.of("messages", nodeId));
        }


    }

    enum CheckpointSaverEnum {
        MEMORY,
        FILE_SYSTEM_JSON {
            @Override
            public FileSystemSaver saver() {
                return new FileSystemSaver(rootPath, jsonStateSerializer  );
            }
        }
        ;

        public BaseCheckpointSaver saver() {
            return new MemorySaver();
        };
    }

    static final Path rootPath = Paths.get( "target", "checkpoint" );

    static final StateSerializer<MyState> jsonStateSerializer =
            new JacksonStateSerializer<>(MyState::new) {};

    static final  StateSerializer<MyState> binStateSerializer =
            new ObjectStreamStateSerializer<>(MyState::new);

    @BeforeAll
    static void init() throws IOException {
        FileSystemSaver.list(rootPath, ( threadId, version ) -> true )
                .forEach(TryConsumer.Try(Files::delete));
    }

    @ParameterizedTest
    @EnumSource( CheckpointSaverEnum.class )
    void interruptAfterEdgeEvaluation( CheckpointSaverEnum saverEnum ) throws Exception {

        var runnableConfig = RunnableConfig.empty();

        try {
            var workflow = new StateGraph<>( MyState.SCHEMA, jsonStateSerializer)
                    .addNode("A", newAction("A"))
                    .addNode("B", newAction("B"))
                    .addNode("C", newAction("C"))
                    .addNode("D", newAction("D"))
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
                            .checkpointSaver(saverEnum.saver())
                            .interruptAfter("B")
                            .releaseThread(false)
                            .build());

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
                                        .filter( s ->
                                                Objects.equals(s.node(),"B") )
                                        .findFirst()
                                        .orElseThrow();

            runnableConfig = workflow.updateState( snapshotForNodeB.config(),
                                    Map.of( "messages", "C"));

            results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
            assertIterableEquals(List.of(
                    "D",
                    END
            ), results);
        }
        finally {
            saverEnum.saver().release(runnableConfig);
        }
    }

    @ParameterizedTest
    @EnumSource( CheckpointSaverEnum.class )
    void interruptBeforeEdgeEvaluation( CheckpointSaverEnum saverEnum ) throws Exception {


        var runnableConfig = RunnableConfig.empty();

        try {
            var workflow = new StateGraph<>( MyState.SCHEMA, jsonStateSerializer)
                    .addNode("A", newAction("A"))
                    .addNode("B", newAction("B"))
                    .addNode("C", newAction("C"))
                    .addConditionalEdges("B",
                            edge_async(state ->
                                    state.lastMessage().orElse(END)),
                            EdgeMappings.builder()
                                    .to("A")
                                    .to("C")
                                    .toEND()
                                    .build())
                    .addEdge(START, "A")
                    .addEdge("A", "B")
                    .addEdge("C", END)
                    .compile(CompileConfig.builder()
                            .checkpointSaver(saverEnum.saver())
                            .interruptAfter("B")
                            .interruptBeforeEdge(true)
                            .build());

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
            results = workflow.stream(GraphInput.resume(Map.of("messages", "C")), runnableConfig)
                    .stream()
                    .peek(System.out::println)
                    .map(NodeOutput::node)
                    .toList();
            assertIterableEquals(List.of(
                    "C",
                    END
            ), results);
        }
        finally {
            saverEnum.saver().release(runnableConfig);
        }
    }


    @ParameterizedTest
    @EnumSource( CheckpointSaverEnum.class )
    void dynamicInterruption( CheckpointSaverEnum saverEnum ) throws Exception {

        var workflow = new StateGraph<>( MyState.SCHEMA, jsonStateSerializer)
                .addNode("A", newAction("A"))
                .addNode("B", newAction("B"))
                .addNode("C", CustomAction.builder()
                                    .nodeId("C")
                                    .interrupt()
                                    .build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saverEnum.saver())
                        .build());

        var runnableConfig = RunnableConfig.empty();

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
    @ParameterizedTest
    @EnumSource( CheckpointSaverEnum.class )
    void nodeOutputAfterStreaming( CheckpointSaverEnum saverEnum ) throws Exception {

        var workflow = new StateGraph<>( MyState.SCHEMA, jsonStateSerializer)
                .addNode("A", CustomAction.builder().nodeId("A").streaming().build() )
                .addNode("B", CustomAction.builder().nodeId("B").build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("B", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saverEnum.saver())
                        .interruptBefore("A", "B")
                        .build());

        var runnableConfig = RunnableConfig.empty();

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

