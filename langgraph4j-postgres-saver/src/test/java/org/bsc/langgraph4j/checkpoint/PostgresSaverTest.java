package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.LogManager;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PostgresSaverTest {


    static class MyJacksonStateSerializer extends JacksonStateSerializer<AgentState> {

        public MyJacksonStateSerializer(AgentStateFactory<AgentState> stateFactory ) {
            super(stateFactory);
        }
    }

    public enum StateSerializerEnum {
        BINARY( new ObjectStreamStateSerializer<>( AgentState::new ) ),
        JSON( new MyJacksonStateSerializer( AgentState::new) );

        final StateSerializer<AgentState> stateSerializer;

        StateSerializerEnum(StateSerializer<AgentState> stateSerializer) {
            this.stateSerializer = stateSerializer;
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostgresSaverTest.class);

    private static final String DATABASE_NAME = "lg4j-store";

    private static final String[] IMAGES = {
            "postgres:16-alpine",
            "pgvector/pgvector:pg16"
    };

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(IMAGES[1])
                            .withDatabaseName(DATABASE_NAME)
                .waitingFor(new CustomPostgreSQLWaitStrategy());

    @BeforeAll
    public static void init() throws IOException {
        // initialize log
        try( var is = PostgresSaverTest.class.getResourceAsStream("/logging.properties") ) {
            if( is!=null ) LogManager.getLogManager().readConfiguration(is);
        }

        // start postgres container
        postgres.start();

    }

    @AfterAll
    public static void shutdown() {
        postgres.stop();
    }

    PostgresSaver.Builder buildPostgresSaver() throws SQLException {
        return PostgresSaver.builder()
                //.host("localhost")
                .host(postgres.getHost())
                //.port(5432)
                .port(postgres.getFirstMappedPort())
                //.user("admin")
                .user(postgres.getUsername())
                //.password("bsorrentino")
                .password(postgres.getPassword())
                .database(DATABASE_NAME)
                ;
    }

    PostgresSaver.Builder buildPostgresSaverWithExistedDatasource() throws SQLException {
        // Simulate a existed datasource
        // Maybe a existed bean in your project
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(DATABASE_NAME);
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setPortNumbers( new int[]{postgres.getFirstMappedPort()} );
        ds.setServerNames( new String[] { postgres.getHost() } );

        return PostgresSaver.builder()
                .datasource(ds);
    }

    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testCheckpointWithReleasedThread( StateSerializerEnum param ) throws Exception {

        var saver = buildPostgresSaver()
                        .dropTablesFirst(true)
                        .stateSerializer( param.stateSerializer  )
                        .build();

        NodeAction<AgentState> agent_1 = state -> {
            log.info( "agent_1");
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async( agent_1 ))
                .addEdge( START,"agent_1")
                .addEdge( "agent_1",  END)
                ;

        var compileConfig = CompileConfig.builder()
                                .checkpointSaver(saver)
                                .releaseThread(true)
                                .build();

        var runnableConfig = RunnableConfig.builder()
                            .build();
        var workflow = graph.compile( compileConfig );

        Map<String, Object> inputs = Map.of( "input", "test1");

        var result = workflow.invoke( inputs, runnableConfig );

        assertTrue( result.isPresent() );

        var history = workflow.getStateHistory( runnableConfig );

        assertTrue( history.isEmpty() );

    }

    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testCheckpointWithNotReleasedThread( StateSerializerEnum param ) throws Exception {
        var saver = buildPostgresSaverWithExistedDatasource()
                        .dropTablesFirst(true)
                        .stateSerializer( param.stateSerializer  )
                        .build();


        NodeAction<AgentState> agent_1 = state -> {
            log.info( "agent_1");
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async( agent_1 ))
                .addEdge( START,"agent_1")
                .addEdge( "agent_1",  END)
                ;

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile( compileConfig );

        Map<String, Object> inputs = Map.of( "input", "test1");

        var result = workflow.invoke( inputs, runnableConfig );

        assertTrue( result.isPresent() );

        var history = workflow.getStateHistory( runnableConfig );

        assertFalse( history.isEmpty() );
        assertEquals( 2, history.size() );

        var lastSnapshot = workflow.lastStateOf( runnableConfig );

        assertTrue( lastSnapshot.isPresent() );
        assertEquals( "agent_1", lastSnapshot.get().node() );
        assertEquals( END, lastSnapshot.get().next() );

        // UPDATE STATE
        final var updatedConfig = workflow.updateState( lastSnapshot.get().config(), Map.of( "update", "update test") );

        var updatedSnapshot = workflow.stateOf( updatedConfig );
        assertTrue( updatedSnapshot.isPresent() );
        assertEquals( "agent_1", updatedSnapshot.get().node() );
        assertTrue( updatedSnapshot.get().state().value("update").isPresent() );
        assertEquals( "update test", updatedSnapshot.get().state().value("update").get() );
        assertEquals( END, lastSnapshot.get().next() );

        // test checkpoints reloading from database
        saver = buildPostgresSaver()
                .stateSerializer( param.stateSerializer  )
                .build(); // create a new saver (reset cache)

        compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        runnableConfig = RunnableConfig.builder().build();
        workflow = graph.compile( compileConfig );

        history = workflow.getStateHistory( runnableConfig );

        assertFalse( history.isEmpty() );
        assertEquals( 2, history.size() );

        lastSnapshot = workflow.stateOf(updatedConfig);
        // lastSnapshot = workflow.lastStateOf( runnableConfig );

        assertTrue( lastSnapshot.isPresent() );
        assertEquals( "agent_1", lastSnapshot.get().node() );
        assertEquals( END, lastSnapshot.get().next() );
        assertTrue( lastSnapshot.get().state().value("update").isPresent() );
        assertEquals( "update test", lastSnapshot.get().state().value("update").get() );
        assertEquals( END, lastSnapshot.get().next() );


        saver.release( runnableConfig );

    }

    @Test
    public void testBuilderSinglePropertyApplied() throws Exception {
        var saver = buildPostgresSaver()
                .property("ApplicationName", "lg4j-property-test")
                .createTables(false)
                .stateSerializer(StateSerializerEnum.BINARY.stateSerializer)
                .build();

        try (var conn = saver.datasource.getConnection();
             var rs = conn.createStatement().executeQuery("SELECT current_setting('application_name')")) {
            assertTrue(rs.next());
            assertEquals("lg4j-property-test", rs.getString(1));
        }
    }

    @Test
    public void testBuilderMultiplePropertiesApplied() throws Exception {
        var props = new Properties();
        props.setProperty("ApplicationName", "lg4j-props-test");
        props.setProperty("connectTimeout", "30");

        var saver = buildPostgresSaver()
                .properties(props)
                .createTables(false)
                .stateSerializer(StateSerializerEnum.BINARY.stateSerializer)
                .build();

        try (var conn = saver.datasource.getConnection();
             var rs = conn.createStatement().executeQuery("SELECT current_setting('application_name')")) {
            assertTrue(rs.next());
            assertEquals("lg4j-props-test", rs.getString(1));
        }
    }

    @Test
    public void testBuilderPropertyIgnoredWithExternalDatasource() throws Exception {
        // when datasource() is provided explicitly, property()/properties() have no effect
        var saver = buildPostgresSaverWithExistedDatasource()
                .property("ApplicationName", "should-not-apply")
                .createTables(false)
                .stateSerializer(StateSerializerEnum.BINARY.stateSerializer)
                .build();

        try (var conn = saver.datasource.getConnection();
             var rs = conn.createStatement().executeQuery("SELECT current_setting('application_name')")) {
            assertTrue(rs.next());
            assertNotEquals("should-not-apply", rs.getString(1));
        }
    }

    /**
     * refer to issue <a href="https://github.com/langgraph4j/langgraph4j/issues/333">#333<a></a>
     */
    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testIssue333( StateSerializerEnum param ) throws SQLException {

        buildPostgresSaver()
                .createTables(true)
                .stateSerializer( param.stateSerializer  )
                .build();

        buildPostgresSaver()
                .createTables(true)
                .stateSerializer( param.stateSerializer  )
                .build();

    }
}
