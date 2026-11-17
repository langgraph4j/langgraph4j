package org.bsc.langgraph4j.checkpoint.studio;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.checkpoint.CustomPostgreSQLWaitStrategy;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.node.Node;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.jetty.LangGraphStudioServer4Jetty;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LangGraphStreamingServerTest {

    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super(initData);
        }
    }

    static class NodeActionBuilder {

        public Node.ActionFactory<State> build( String nodeId ) {
            assertNotNull( nodeId );
            return ( CompileConfig compileConfig ) ->

                    (state,config) -> {

                        assertEquals(nodeId, config.nodeId());

                        return completedFuture( Map.of(MessagesState.MESSAGES, "%s-%d".formatted(nodeId, System.nanoTime() )));
                    };

        }
    }

    static NodeActionBuilder nodeBuilder() {
        return new NodeActionBuilder();
    };

    static PostgresSaver.Builder buildPostgresSaver( PostgreSQLContainer<?> postgres, String databaseName ) throws SQLException {
        return PostgresSaver.builder()
                .host(postgres.getHost())
                .port(postgres.getFirstMappedPort())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .database(databaseName)
                .stateSerializer(new ObjectStreamStateSerializer<>( AgentState::new ) )
                ;
    }

    static StateGraph<State> buildWorkflow() throws GraphStateException {

        AsyncCommandAction<State> edgeAction = (state, config ) ->
                completedFuture( (state.messages().size() > 3) ?
                    new Command( END ) :
                    new Command( "action"));


        return new StateGraph<>(State.SCHEMA, State::new)
                .addNode("agent", nodeBuilder().build("agent") )
                .addNode("action", nodeBuilder().build("action") )
                .addEdge(START, "agent")
                .addConditionalEdges( "agent", edgeAction,
                        EdgeMappings.builder()
                                .toEND()
                                .to( "action" )
                                .build() )
                .addEdge("action", "agent" )
                ;

    }


    public static void main(String[] args) throws Exception {

        try (var postgres =
                     new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                             .withDatabaseName("lg4j-store")
                             .waitingFor(new CustomPostgreSQLWaitStrategy())) {

            postgres.start();

            var saver = buildPostgresSaver(postgres, "lg4j-store")
                    .dropTablesFirst(true)
                    .build();

            var workflow = buildWorkflow();

            var noReleaseThread = LangGraphStudioServer.Instance.builder()
                    .title("LangGraph4j Studio - No release thread")
                    .addInputStringArg("input")
                    .graph(workflow)
                    .compileConfig(CompileConfig.builder()
                            .checkpointSaver(saver)
                            .build())
                    .build();

            var autoReleaseThread = LangGraphStudioServer.Instance.builder()
                    .title("LangGraph4j Studio - Auto release thread")
                    .addInputStringArg("input")
                    .graph(workflow)
                    .compileConfig(CompileConfig.builder()
                            .checkpointSaver(saver)
                            .releaseThread(true)
                            .build())
                    .build();

            LangGraphStudioServer4Jetty.builder()
                    .port(8080)
                    .instance("no_release_thread", noReleaseThread)
                    .instance("auto_release_thread", autoReleaseThread)
                    .build()
                    .start();

            System.out.println("press <enter> to finish");
            System.console().readLine();
            System.exit(0);
        }
    }

  }
