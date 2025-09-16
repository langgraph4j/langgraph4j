package org.bsc.langgraph4j.jetty;

import jakarta.servlet.*;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.jetty.LangGraphStudioServer4Jetty;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class LangGraphStreamingServerTest {

    static class State extends AgentState {

        public static final String AGENT_RESPONSE = "agent_response";
        public static final String ACTION_RESPONSE = "action_response";

        public State(Map<String, Object> initData) {
            super(initData);
        }

        String input() {
            return this.<String>value("input").orElseThrow();
        }

        Optional<String> agentResponse() {
            return this.value(AGENT_RESPONSE);
        }

        Optional<String> actionResponse() {
            return this.value(ACTION_RESPONSE);
        }
    }

    static StateGraph<State> buildWorkflow() throws GraphStateException {

        EdgeAction<State> conditionalAge = (state) -> {
            System.out.println("condition");
            System.out.println(state);

            return state.agentResponse()
                    .filter(res ->!res.isBlank())
                    .map(res -> "end" )
                    .orElse("action");
        };

        NodeAction<State> agentNode = (state ) -> {

            System.out.println("agent ");
            System.out.println(state);

            Thread.sleep( 2000 );

            if( state.actionResponse().map( res -> !res.isBlank() ).orElse(false) ) {
                return Map.of(State.AGENT_RESPONSE, "We have successfully completed your request: " + state.input());
            }

            return Map.of( State.AGENT_RESPONSE, "");


        };

        AsyncNodeAction<State> actionNode = (state ) -> {

            var result = new CompletableFuture<Map<String,Object>>();

            System.out.println("action ");
            System.out.println(state);

            if( state.agentResponse().map( res -> !res.isBlank() ).orElse(false) ) {
                result.complete(Map.of(State.ACTION_RESPONSE, "skipped"));
            }
            else {
                try {
                    Thread.sleep(2000);

                    result.complete(Map.of(State.ACTION_RESPONSE, "the job request '" + state.input() + "' has been completed"));

                } catch (InterruptedException e) {
                    result.completeExceptionally(e);
                }
            }
            return result;

        };

        return new StateGraph<>(State::new)
                .addNode("agent", node_async(agentNode) )
                .addNode("action", actionNode )
                .addEdge(START, "agent")
                .addEdge("action", "agent" )
                .addConditionalEdges("agent",
                        edge_async(conditionalAge),
                        EdgeMappings.builder()
                                .to("action")
                                .toEND( "end" )
                                .build() )
                ;

    }

    public static void main(String[] args) throws Exception {
        var workflow = buildWorkflow();
        var saver = new MemorySaver();

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

        var withInterruption = LangGraphStudioServer.Instance.builder()
                .title("LangGraph4j Studio - With interruption")
                .addInputStringArg("input")
                .graph(workflow)
                .compileConfig(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .releaseThread(true)
                        .interruptBefore("action")
                        .build())
                .build();


        LangGraphStudioServer4Jetty.builder()
                .port(8080)
                .instance( "no_release_thread", noReleaseThread )
                .instance( "auto_release_thread", autoReleaseThread )
                .instance( "with_interruption", withInterruption )
                .filter( ctx -> ctx.addFilter(CorsFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST)))
                .build()
                .start()
                .join();

    }


  }
