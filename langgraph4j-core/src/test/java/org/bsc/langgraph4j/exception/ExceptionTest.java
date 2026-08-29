package org.bsc.langgraph4j.exception;

import org.bsc.langgraph4j.GraphInterruptException;
import org.bsc.langgraph4j.GraphRunnerException;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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

}
