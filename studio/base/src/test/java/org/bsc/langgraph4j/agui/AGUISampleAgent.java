package org.bsc.langgraph4j.agui;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agui.sdk.AGUIHook;
import org.bsc.langgraph4j.annotation.ActionCatalog;
import org.bsc.langgraph4j.annotation.ActionCatalogRepository;
import org.bsc.langgraph4j.annotation.EdgeCondition;
import org.bsc.langgraph4j.annotation.NodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.studio.agui.AGUILangGraphStudioAgent;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

public interface AGUISampleAgent {


    static AGUILangGraphStudioAgent baseAgent(String id) throws GraphStateException {

        final var actions = new ActionCatalog() {

            @NodeAction
            public Map<String, Object> action(AgentState state, RunnableConfig config) throws Exception {
                return Map.of();
            }

            @EdgeCondition
            public Command route( AgentState state, RunnableConfig config)  {
                if( state.<Boolean>value("tool_invoked").orElse(false) )
                    return new Command(END);
                return new Command("tools", Map.of("tool_invoked", true));
            }
        };

        final var repo = ActionCatalogRepository.of(actions);

        final var graph =  new StateGraph<>(AgentState::new)
                .addWrapCallNodeHook(AGUIHook.stepEvents())
                .add( repo.node("model", "action" ))
                .add( repo.node( "tools", "action"))
                .addEdge(START, "model")
                .add( repo.edge("model",
                                "route",
                                EdgeMappings.builder()
                                        .to("tools")
                                        .toEND()
                                        .build()))
                .addEdge("tools", "model");
        return AGUILangGraphStudioAgent.builder()
                .id(id)
                .title("LangGraph Studio (Base Agent)")
                .stateGraph(graph)
                //.addInputStringArg("input")
                .build();

    }

    static AGUILangGraphStudioAgent agentWithInterruption(String id) throws GraphStateException { {


            class State extends MessagesState<String> {
                static final Map<String, Channel<?>> SCHEMA = mergeMap(
                        Map.of("resumeToNode", Channels.base( () -> "") ),
                        MessagesState.SCHEMA
                );

                public State(Map<String, Object> initData) {
                    super(initData);
                }
            }

            final var actions = new ActionCatalog() {

                @NodeAction
                public Map<String, Object> action(State state, RunnableConfig config) throws Exception {
                    return Map.of("messages",config.nodeId());
                }
                @EdgeCondition
                public Command route( State state, RunnableConfig config)  {
                    final var gotNode =  state.<String>value("resumeToNode").orElse(END);

                    return new Command(gotNode, Map.of( "resumeToNode", MARK_FOR_REMOVAL));
                }
            };

            final var repo = ActionCatalogRepository.of(actions);

            final var stateSerializer = new JacksonStateSerializer<>(State::new);

            var graph = new StateGraph<>( State.SCHEMA, stateSerializer)
                    .addWrapCallNodeHook(AGUIHook.stepEvents())
                    .add( repo.node("A", "action") )
                    .add( repo.node("B", "action") )
                    .add( repo.node("C", "action") )
                    .add( repo.edge("B",
                            "route",
                            EdgeMappings.builder()
                                    .to("A")
                                    .to("C")
                                    .toEND()
                                    .build()) )
                    .addEdge(START, "A")
                    .addEdge("A", "B")
                    .addEdge("C", END);

            final var compileConfig = CompileConfig.builder()
                    .checkpointSaver(new MemorySaver())
                    .interruptAfter("B")
                    .interruptBeforeEdge(true)
                    .build();

            return AGUILangGraphStudioAgent.builder()
                .id(id)
                .title("LangGraph Studio (Interruption Demo)")
                .stateGraph(graph)
                .compileConfig(compileConfig)
                .build();

        }

    }

}
