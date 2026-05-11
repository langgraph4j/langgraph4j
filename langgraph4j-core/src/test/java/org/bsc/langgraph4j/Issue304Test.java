package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test for issue <a href="https://github.com/langgraph4j/langgraph4j/issues/304">#304</a>
 */
public class Issue304Test {

    static class NodeActionTest implements NodeAction<AgentState>, InterruptableAction<AgentState> {

        @Override
        public Map<String, Object> apply(AgentState state) throws Exception {
            return Map.of("k1", "v1");
        }

        @Override
        public Optional<InterruptionMetadata<AgentState>> interrupt(String nodeId, AgentState state, RunnableConfig config) {
            return Optional.of( InterruptionMetadata.builder(nodeId,state).build() );
        }
    }

    static class NodeActionWithConfigTest implements NodeActionWithConfig<AgentState>, InterruptableAction<AgentState> {

        @Override
        public Map<String, Object> apply(AgentState state, RunnableConfig config)  {
            return Map.of("k1", "v1");
        }

        @Override
        public Optional<InterruptionMetadata<AgentState>> interrupt(String nodeId, AgentState state, RunnableConfig config) {
            return Optional.of( InterruptionMetadata.builder(nodeId,state).build() );
        }
    }

    static class AsyncNodeActionTest implements AsyncNodeAction<AgentState>, InterruptableAction<AgentState> {

        @Override
        public CompletableFuture<Map<String, Object>> apply(AgentState state)  {
            return completedFuture(Map.of("k1", "v1"));
        }

        @Override
        public Optional<InterruptionMetadata<AgentState>> interrupt(String nodeId, AgentState state, RunnableConfig config) {
            return Optional.of( InterruptionMetadata.builder(nodeId,state).build() );
        }
    }

    @Test
    void nodeActionProxy() throws Exception {
        final var action = new NodeActionTest();

        var proxyInstance = Proxy.newProxyInstance( getClass().getClassLoader(),
                new Class[] { NodeAction.class, InterruptableAction.class},
                (proxy, method, methodArgs) -> {
                    return method.invoke(action, methodArgs);
                });

        assertInstanceOf(InterruptableAction.class, proxyInstance);
        assertInstanceOf(NodeAction.class, proxyInstance);

        @SuppressWarnings("unchecked")
        final var nodeAction = (NodeAction<AgentState>)proxyInstance;

        var state = new AgentState(Map.of());

        var result = nodeAction.apply( state );
        assertInstanceOf(Map.class, result);
        assertIterableEquals( Map.of("k1", "v1").entrySet(), result.entrySet() );

        @SuppressWarnings("unchecked")
        final var interruptableAction = (InterruptableAction<AgentState>)proxyInstance;

        var resultMetadata = interruptableAction.interrupt( "node1", state, RunnableConfig.empty() );
        assertInstanceOf(Optional.class, resultMetadata);
        assertTrue( resultMetadata.isPresent() );
        assertInstanceOf(InterruptionMetadata.class, resultMetadata.get());
        assertEquals( "node1", resultMetadata.get().nodeId() );
        assertEquals( state, resultMetadata.get().state() );
    }

    @Test
    void nodeActionWrapper() {
        var state = new AgentState(Map.of());

        var nodeAction = AsyncNodeAction.node_async( new NodeActionTest() );

        var result = nodeAction.apply( state ).join();
        assertInstanceOf(Map.class, result);
        assertIterableEquals( Map.of("k1", "v1").entrySet(), result.entrySet() );

        assertInstanceOf(InterruptableAction.class, nodeAction);

        @SuppressWarnings("unchecked")
        final var interruptableAction = (InterruptableAction<AgentState>)nodeAction;

        var resultMetadata = interruptableAction.interrupt( "node1", state, RunnableConfig.empty() );
        assertInstanceOf(Optional.class, resultMetadata);
        assertTrue( resultMetadata.isPresent() );
        assertInstanceOf(InterruptionMetadata.class, resultMetadata.get());
        assertEquals( "node1", resultMetadata.get().nodeId() );
        assertEquals( state, resultMetadata.get().state() );

    }

    @Test
    void nodeActionWithConfigWrapper() {
        var state = new AgentState(Map.of());

        var nodeAction = AsyncNodeActionWithConfig.node_async( new NodeActionWithConfigTest() );

        var result = nodeAction.apply( state, RunnableConfig.empty() ).join();
        assertInstanceOf(Map.class, result);
        assertIterableEquals( Map.of("k1", "v1").entrySet(), result.entrySet() );

        assertInstanceOf(InterruptableAction.class, nodeAction);

        @SuppressWarnings("unchecked")
        final var interruptableAction = (InterruptableAction<AgentState>)nodeAction;

        var resultMetadata = interruptableAction.interrupt( "node1", state, RunnableConfig.empty() );
        assertInstanceOf(Optional.class, resultMetadata);
        assertTrue( resultMetadata.isPresent() );
        assertInstanceOf(InterruptionMetadata.class, resultMetadata.get());
        assertEquals( "node1", resultMetadata.get().nodeId() );
        assertEquals( state, resultMetadata.get().state() );

    }

    @Test
    void asyncNodeActionConversionToAsyncNodeActionWithConfig() {
        var state = new AgentState(Map.of());

        var asyncNodeAction = AsyncNodeActionWithConfig.of( new AsyncNodeActionTest() );

        var result = asyncNodeAction.apply( state, RunnableConfig.empty() ).join();
        assertInstanceOf(Map.class, result);
        assertIterableEquals( Map.of("k1", "v1").entrySet(), result.entrySet() );

        assertInstanceOf(InterruptableAction.class, asyncNodeAction);

        @SuppressWarnings("unchecked")
        final var interruptableAction = (InterruptableAction<AgentState>)asyncNodeAction;

        var resultMetadata = interruptableAction.interrupt( "node1", state, RunnableConfig.empty() );
        assertInstanceOf(Optional.class, resultMetadata);
        assertTrue( resultMetadata.isPresent() );
        assertInstanceOf(InterruptionMetadata.class, resultMetadata.get());
        assertEquals( "node1", resultMetadata.get().nodeId() );
        assertEquals( state, resultMetadata.get().state() );

    }

}
