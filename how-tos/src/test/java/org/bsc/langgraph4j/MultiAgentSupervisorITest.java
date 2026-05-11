package org.bsc.langgraph4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

public class MultiAgentSupervisorITest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MultiAgentSupervisorITest.class);

   static class State extends MessagesState<ChatMessage> {

        public Optional<String> next() {
            return this.value("next");
        }

        public State(Map<String, Object> initData) {
            super( initData  );
        }
    }

    static class StateSerializer extends ObjectStreamStateSerializer<State> {

        public StateSerializer() {
            super(State::new);

            mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
            mapper().register(ChatMessage.class, new ChatMesssageSerializer());
        }
    }

    static class SupervisorAgent implements AsyncNodeAction<State> {


        static class Router {
            @Description("Worker to route to next. If no workers needed, route to FINISH.")
            String next;

            @Override
            public String toString() {
                return format( "Router[next: %s]",next);
            }
        }

        interface Service {
            @SystemMessage( """
                    You are a supervisor tasked with managing a conversation between the following workers: {{members}}.
                    Given the following user request, respond with the worker to act next.
                    Each worker will perform a task and respond with their results and status.
                    When finished, respond with FINISH.
                    """)
            Router evaluate(@V("members") String members, @dev.langchain4j.service.UserMessage  String userMessage);
        }

        final Service service;
        public final String[] members = {"researcher", "coder" };

        public SupervisorAgent(ChatModel model ) {

            service = AiServices.create( Service.class, model );
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state) {
            var m = String.join(",", members);
            var message = state.lastMessage().orElseThrow();
            var text = switch( message.type() ) {
                case USER -> ((UserMessage)message).singleText();
                case AI -> ((AiMessage)message).text();
                default -> throw new IllegalStateException("unexpected message type: " + message.type() );
            };

            var result = service.evaluate( m, text );
            return CompletableFuture.completedFuture(Map.of( "next", result.next ));
        }
    }


    static class ResearchAgent implements AsyncNodeAction<State> {
        static class Tools {

            @Tool("""
            Use this to perform a research over internet
            """)
            String search(@P("internet query") String query) {
                log.info( "search query: '{}'", query );
                return """
                the games will be in Italy at Cortina '2026
                """;
            }
        }

        interface Service {
            String search(@dev.langchain4j.service.UserMessage  String query);
        }

        final Service service;

        public ResearchAgent( ChatModel model ) {
            service = AiServices.builder( Service.class )
                            .chatModel(model)
                            .tools( new Tools() )
                            .build();
        }
        @Override
        public CompletableFuture<Map<String, Object>> apply(State state) {
            var message = state.lastMessage().orElseThrow();
            var text = switch( message.type() ) {
                case USER -> ((UserMessage)message).singleText();
                case AI -> ((AiMessage)message).text();
                default -> throw new IllegalStateException("unexpected message type: " + message.type() );
            };
            var result = service.search( text );
            return CompletableFuture.completedFuture(Map.of( "messages", AiMessage.from(result) ));

        }
    }

    static class CoderAgent implements AsyncNodeAction<State> {
        static class Tools {

            @Tool("""
                Use this to execute java code and do math. If you want to see the output of a value,
                you should print it out with `System.out.println(...);`. This is visible to the user.""")
            String search(@P("coder request") String request) {
                log.info( "CoderTool request: '{}'", request );
                return """
                2
                """;
            }
        }

        interface Service {
            String evaluate(@dev.langchain4j.service.UserMessage String code);
        }

        final Service service;

        public CoderAgent( ChatModel model ) {
            service = AiServices.builder( Service.class )
                    .chatModel(model)
                    .tools( new Tools() )
                    .build();
        }
        @Override
        public CompletableFuture<Map<String, Object>> apply(State state) {
            var message = state.lastMessage().orElseThrow();
            var text = switch( message.type() ) {
                case USER -> ((UserMessage)message).singleText();
                case AI -> ((AiMessage)message).text();
                default -> throw new IllegalStateException("unexpected message type: " + message.type() );
                };
            var result = service.evaluate( text );
            return CompletableFuture.completedFuture(Map.of( "messages", AiMessage.from(result) ));

        }
    }

    final ChatModel model = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .responseFormat( ResponseFormat.JSON)
                // .format( "json" )
                .modelName("deepseek-r1:14b")
                //.modelName("llama3.2:latest")
                .build();

    final ChatModel modelWithTool = OllamaChatModel.builder()
            .baseUrl( "http://localhost:11434" )
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .modelName("llama3.1:latest")
            .build();

    @Test
    public void testSupervisorService() {

        /*
        // SET OUTPUT SCHEMA
        var responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON) // type can be either TEXT (default) or JSON
                .jsonSchema(JsonSchema.builder()
                        .name("Person") // OpenAI requires specifying the name for the schema
                        .rootElement(JsonObjectSchema.builder() // see [1] below
                                .addStringProperty("name")
                                .addIntegerProperty("age")
                                .addNumberProperty("height")
                                .addBooleanProperty("married")
                                .required("name", "age", "height", "married") // see [2] below
                                .build())
                        .build())
                .build();
        */

        var supervisor = new SupervisorAgent( model );

        var result = supervisor.service.evaluate(
                String.join(",", supervisor.members),
                "where are next winter olympic games ?"  );

        log.info( "SUPERVISOR RESULT {}", result);

    }

    @Test
    public void testResearcherService() {

        var researcher = new ResearchAgent(modelWithTool);

        var result = researcher.service.search( "where are next winter olympic games ?"  );

        log.info( "RESEARCHER RESULT {}", result);

    }

    @Test
    public void testCoderService() {

        var coder = new CoderAgent(modelWithTool);

        var result = coder.service.evaluate( "what are the result of 1 + 1 ?"  );

        log.info( "CODER RESULT {}", result);

    }

    @Test
    void buildAndRunGraph() throws Exception {

        var workflow = new StateGraph<>( State.SCHEMA, new StateSerializer() )
                .addNode( "supervisor", new SupervisorAgent(model) )
                .addNode( "coder", new CoderAgent(modelWithTool) )
                .addNode( "researcher", new ResearchAgent(modelWithTool) )
                .addEdge( START, "supervisor")
                .addConditionalEdges( "supervisor",
                        edge_async( state ->
                            state.next().orElseThrow()
                        ), Map.of(
                                "FINISH", END,
                                "coder", "coder",
                                "researcher", "researcher"
                        ))
                .addEdge( "coder", "supervisor")
                .addEdge( "researcher", "supervisor")
                ;
        var graph = workflow.compile();

        for( var event : graph.stream( GraphInput.args(Map.of( "messages", UserMessage.from("what are the result of 1 + 1 ?"))),
                                        RunnableConfig.empty() ) )  {
            log.info( "{}", event );
        }

        for( var event : graph.stream( GraphInput.args(Map.of( "messages", UserMessage.from("where are next winter olympic games ?" ))),
                                        RunnableConfig.empty()) ) {
            log.info( "{}", event );
        }

    }
}


