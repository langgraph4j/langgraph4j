package org.bsc.langgraph4j.studio.jetty;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises browser CORS requests against the embedded Studio routing and bundled assets.
 * Each test owns its server and uses an in-memory connector; no browser, socket, or LLM is needed.
 */
class StudioCorsTest {

    /**
     * Verifies that JSON preflight reaches the filter before a real graph streaming POST.
     * Also checks that initialization and bundled static content remain accessible.
     *
     * @throws Exception if server startup, request handling, or cleanup fails
     */
    @Test
    void preflightAndStreamingPostIncludeCorsHeaders() throws Exception {
        // Use a deterministic graph so the POST exercises streaming without an external model.
        var graph = new StateGraph<>(AgentState::new)
                .addNode("reply", node_async(state -> Map.of("result", "cors-stream-ok")))
                .addEdge(StateGraph.START, "reply")
                .addEdge("reply", StateGraph.END);
        var studio = LangGraphStudioServer4Jetty.builder()
                .instance("test", LangGraphStudioServer.Instance.builder().graph(graph).build())
                .cors("http://localhost:1234")
                .build();
        var connector = new LocalConnector(studio.server);
        // Replace the TCP connector so this integration test does not bind a listening port.
        studio.server.setConnectors(new Connector[]{connector});

        try {
            studio.start().join();

            // Simple GET requests establish the baseline that works without a preflight.
            var init = HttpTester.parseResponse(connector.getResponse(
                    "GET /init HTTP/1.1\r\nHost: localhost\r\nOrigin: http://localhost:1234\r\n\r\n"));
            assertEquals(200, init.getStatus());
            assertEquals("http://localhost:1234", init.get("Access-Control-Allow-Origin"));

            // Browsers must receive approval for the JSON content type before sending the POST.
            var preflight = HttpTester.parseResponse(connector.getResponse(
                    "OPTIONS /stream/test?thread=default HTTP/1.1\r\nHost: localhost\r\n"
                            + "Origin: http://localhost:1234\r\nAccess-Control-Request-Method: POST\r\n"
                            + "Access-Control-Request-Headers: content-type\r\n\r\n"));
            assertEquals(200, preflight.getStatus());
            assertEquals("http://localhost:1234", preflight.get("Access-Control-Allow-Origin"));
            assertTrue(preflight.get("Access-Control-Allow-Methods").contains("POST"));
            assertTrue(preflight.get("Access-Control-Allow-Headers").contains("Content-Type"));

            // Require graph output as well as headers so servlet errors cannot pass as successful streaming.
            var stream = HttpTester.parseResponse(connector.getResponse(
                    "POST /stream/test?thread=default HTTP/1.1\r\nHost: localhost\r\n"
                            + "Origin: http://localhost:1234\r\nContent-Type: application/json\r\n"
                            + "Content-Length: 2\r\n\r\n{}")); // The empty JSON object is two ASCII bytes.
            assertEquals(200, stream.getStatus());
            assertEquals("http://localhost:1234", stream.get("Access-Control-Allow-Origin"));
            assertTrue(stream.getContent().contains("cors-stream-ok"), stream.getContent());

            // Moving resource handling must preserve the packaged Studio page.
            var page = HttpTester.parseResponse(connector.getResponse(
                    "GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n"));
            assertEquals(200, page.getStatus());
            assertTrue(page.getContent().contains("<html"));
        } finally {
            // Stop Jetty even when an assertion fails to release all server-owned threads.
            studio.server.stop();
        }
    }
}
