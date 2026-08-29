package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
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

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class LG4JInterruptionTest implements LG4JTestUtil{

    enum CheckpointSaverEnum {
        MEMORY,
        FILE_SYSTEM_JSON {
            @Override
            public FileSystemSaver saver() {
                return new FileSystemSaver(rootPath, StateSerializerEnum.JSON.stateSerializer);
            }
        }
        ;

        public BaseCheckpointSaver saver() {
            return new MemorySaver();
        };
    }

    static final Path rootPath = Paths.get( "target", "checkpoint" );

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
            var workflow = new StateGraph<>( State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                    .addNode("A", CustomNodeAction.of("A"))
                    .addNode("B", CustomNodeAction.of("B"))
                    .addNode("C", CustomNodeAction.of("C"))
                    .addNode("D", CustomNodeAction.of("D"))
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
            var workflow = new StateGraph<>( State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                    .addNode("A", CustomNodeAction.of("A"))
                    .addNode("B", CustomNodeAction.of("B"))
                    .addNode("C", CustomNodeAction.of("C"))
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

        var workflow = new StateGraph<>( State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                .addNode("A", CustomNodeAction.of("A"))
                .addNode("B", CustomNodeAction.of("B"))
                .addNode("C", CustomNodeAction.builder()
                                    .message("C")
                                    .interruptable()
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

        results = workflow.stream( GraphInput.resume( Map.of(State.RESUME, true)), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "C",
                END
        ), results );

    }

    @ParameterizedTest
    @EnumSource( CheckpointSaverEnum.class )
    void interruptWithException(  CheckpointSaverEnum saverEnum ) throws Exception {

        var workflow = new StateGraph<>( State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                .addNode("A", CustomNodeAction.of("A"))
                .addNode("B", CustomNodeAction.of("B"))
                .addNode("C", CustomNodeAction.builder()
                        .message("raise exception in node C")
                        .interruptWithException()
                        .build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver( saverEnum.saver() )
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

        results = workflow.stream( GraphInput.resume( Map.of(State.RESUME, true)), runnableConfig )
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

        var workflow = new StateGraph<>( State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                .addNode("A", CustomNodeAction.builder().message("A").streaming().build() )
                .addNode("B", CustomNodeAction.builder().message("B").build())
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

