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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;
import static org.junit.jupiter.api.Assertions.*;

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

        if( Files.exists(rootPath) ) {
            try(var stream = Files.list(rootPath)) {
                stream.filter( Files::isRegularFile )
                        .forEach(TryConsumer.Try(Files::delete));
            }
        }
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

            var generator  = workflow.stream(GraphInput.resume(), runnableConfig );

            results = generator.stream()
                    .peek(System.out::println)
                    .map(NodeOutput::node)
                    .toList();
            assertIterableEquals(List.of(
                    "D",
                    END
            ), results );

            final var result = GraphResult.from(generator);
            assertTrue(result.isCheckpointSaverTag());

            final var tag = result.asCheckpointSaverTag();
            assertNotNull(tag);

            final var checkpoint = tag.checkpoints().stream()
                    .filter( c -> Objects.equals(c.nodeId(),"B") )
                    .findFirst()
                    .orElseThrow()
                    .updateState(Map.of("messages", List.of("C")), workflow.stateGraph.schema());


            results = workflow.stream(GraphInput.resume(checkpoint), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
            assertIterableEquals(List.of(
                    "D",
                    END
            ), results);

        } catch (Exception e) {
            saverEnum.saver().releaseOnError(runnableConfig, e);
        }
    }

    @ParameterizedTest
    @EnumSource( CheckpointSaverEnum.class )
    void interruptAfterEdgeEvaluationWithOutputMapper( CheckpointSaverEnum saverEnum ) throws Exception {

        var runnableConfig = RunnableConfig.builder().threadId("output-mapper").build();

        try {
            var workflow = new StateGraph<>( State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                    .withOutputMapper( data -> {
                        final var lastMessage = lastOf((List<?>)data.get("messages"));

                        return lastMessage.map(message -> Map.<String,Object>of("messages", List.of(message)))
                                .orElseGet(Map::of);
                    })
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
                            .build());

            var generator = workflow.stream(GraphInput.noArgs(), runnableConfig);

            generator.reduce( new ArrayList<String>(), (steps, output) -> {
                        steps.add(output.node());
                        return steps;
                    })
                    .thenAccept(steps -> {
                        assertIterableEquals(List.of(
                                START,
                                "A",
                                "B"
                        ), steps);
                    }).join();
            var result = GraphResult.from(generator);
            assertTrue(result.isInterruptionMetadata());
            assertEquals( List.of("A", "B"), result.<State>asInterruptionMetadata().state().messages() );

            generator = workflow.stream(GraphInput.resume(), runnableConfig );

            generator.reduce( new ArrayList<String>(), (steps, output) -> {
                        steps.add(output.node());
                        return steps;
                    })
                    .thenAccept(steps -> {
                        assertIterableEquals(List.of(
                                "D",
                                END
                        ), steps);
                    }).join();

            result = GraphResult.from(generator);
            assertTrue(result.isStateData());
            assertEquals( List.of("D"), result.<State>asState().messages() );

            final var tag = result.asCheckpointSaverTag();
            assertNotNull(tag);

            final var checkpoint = tag.checkpoints().stream()
                    .filter( c -> Objects.equals(c.nodeId(),"B") )
                    .findFirst()
                    .orElseThrow()
                    .updateState(Map.of("messages", List.of("C")), workflow.stateGraph.schema());

            generator = workflow.stream(GraphInput.resume(checkpoint), runnableConfig );

            generator.reduce( new ArrayList<String>(), (steps, output) -> {
                        steps.add(output.node());
                        return steps;
                    })
                    .thenAccept(steps -> {
                        assertIterableEquals(List.of(
                                "D",
                                END
                        ), steps);
                    }).join();
            result = GraphResult.from(generator);
            assertTrue(result.isStateData());
            assertEquals( List.of("D"), result.<State>asState().messages() );

        } catch (Exception e) {
            saverEnum.saver().releaseOnError(runnableConfig, e);
            fail(e);
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

