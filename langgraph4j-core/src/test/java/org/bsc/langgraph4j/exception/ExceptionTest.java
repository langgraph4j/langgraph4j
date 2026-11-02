package org.bsc.langgraph4j.exception;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.annotation.ActionCatalog;
import org.bsc.langgraph4j.annotation.ActionCatalogRepository;
import org.bsc.langgraph4j.annotation.NodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionTest {


    void raiseException1() throws Exception {
        throw new GraphInterruptException(RunnableConfig.empty(), "need to interrupt graph execution");
    }
    void raiseException2() throws Exception {
        throw new GraphRunnerException(RunnableConfig.empty(), "error on execution");
    }

    @Test
    void handleInterruptionExceptionCase1() {
        try {
            raiseException1();
        } catch (GraphInterruptException e2) {
            // Handle the GraphInterruptException here
            assertEquals("need to interrupt graph execution", e2.getMessage());
        } catch (GraphRunnerException e1) {
            fail("Expected GraphInterruptException, but caught GraphRunnerException");
        } catch (Exception e) {
            fail("Expected GraphInterruptException, but caught Exception");
        }
    }

    @Test
    void handleRunnerExceptionCase2() {
        try {
            raiseException2();
        } catch (GraphInterruptException e2) {
            fail("Expected GraphRunnerException, but caught GraphInterruptException");
        } catch (GraphRunnerException e1) {
            // Handle the GraphRunnerException here
            assertEquals("error on execution", e1.getMessage());
        } catch (Exception e) {
            fail("Expected GraphInterruptException, but caught Exception");
        }
    }


    @Test
    void testActionExceptionTypeIsPreserved() throws GraphStateException {

        var eex = ExceptionUtils.unwrap(null);

        class State extends MessagesState<String> {
            public State(Map<String, Object> initData) {
                super(initData);
            }
        }

        class OuterException extends RuntimeException {
            OuterException(String message, Throwable cause) { super(message, cause); }
        }
        class MyActionCatalog implements ActionCatalog {
            @NodeAction(name = "node_with_exception")
            public Map<String, Object> nodeAction(State state, RunnableConfig config) {
                throw new OuterException("outer", new java.io.IOException("inner"));
            }
        }

        final var repo = ActionCatalogRepository.of(new MyActionCatalog());

        final var workflow = new StateGraph<>(MessagesState.SCHEMA, State::new)
                .add(repo.node("node_with_exception"))
                .addEdge(START, "node_with_exception")
                .addEdge("node_with_exception", END)
                .compile();

        final var ex = assertThrows(Exception.class,
                () -> workflow.invoke(GraphInput.noArgs(), RunnableConfig.empty()));

        assertTrue(ExceptionUtils.findCauseByType(ex, OuterException.class).isPresent(),
                "the exception thrown by the action should be preserved");
    }
}
