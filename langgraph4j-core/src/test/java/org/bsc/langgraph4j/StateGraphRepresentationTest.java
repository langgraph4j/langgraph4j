package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateGraphRepresentationTest {

    CompletableFuture<Map<String, Object>> dummyNodeAction(AgentState state) {
        return CompletableFuture.completedFuture(Map.of());
    }

    CompletableFuture<String> dummyCondition(AgentState state) {
        return CompletableFuture.completedFuture("");
    }

    @Test
    public void testSimpleGraph() throws Exception {

        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("agent_3", this::dummyNodeAction)
                .addNode("agent_1", this::dummyNodeAction)
                .addNode("agent_2", this::dummyNodeAction)
                .addEdge(START, "agent_1")
                .addEdge("agent_2", END)
                .addEdge("agent_1", "agent_3")
                .addEdge("agent_3", "agent_2");

        CompiledGraph<AgentState> app = workflow.compile();

        GraphRepresentation result = app.getGraph(GraphRepresentation.Type.PLANTUML);
        assertEquals(GraphRepresentation.Type.PLANTUML, result.type());

        assertEquals("""
                @startuml Graph_Diagram
                skinparam usecaseFontSize 14
                skinparam usecaseStereotypeFontSize 12
                skinparam hexagonFontSize 14
                skinparam hexagonStereotypeFontSize 12
                title "Graph Diagram"
                footer
                powered by langgraph4j
                end footer
                circle start<<input>> as __START__
                circle stop as __END__
                usecase "agent_3"<<Node>>
                usecase "agent_1"<<Node>>
                usecase "agent_2"<<Node>>
                "__START__" -down-> "agent_1"
                "agent_2" -down-> "__END__"
                "agent_1" -down-> "agent_3"
                "agent_3" -down-> "agent_2"
                @enduml
                """, result.content());

        // System.out.println( result.getContent() );
    }

    @Test
    public void testCorrectionProcessGraph() throws Exception {

        var workflow = new StateGraph<>(AgentState::new)
                .addNode("evaluate_result", this::dummyNodeAction)
                .addNode("agent_review", this::dummyNodeAction)
                .addEdge("agent_review", "evaluate_result")
                .addConditionalEdges(
                        "evaluate_result",
                        this::dummyCondition,
                        EdgeMappings.builder()
                                .toEND("OK")
                                .toEND("UNKNOWN")
                                .to("agent_review", "ERROR" )
                                .build()
                )
                .addEdge(START, "evaluate_result");

        var result = workflow.getGraph(GraphRepresentation.Type.PLANTUML, "Correction process");

        assertEquals(GraphRepresentation.Type.PLANTUML, result.type());

        assertEquals("""
                       @startuml Correction_process
                       skinparam usecaseFontSize 14
                       skinparam usecaseStereotypeFontSize 12
                       skinparam hexagonFontSize 14
                       skinparam hexagonStereotypeFontSize 12
                       title "Correction process"
                       footer
                       powered by langgraph4j
                       end footer
                       circle start<<input>> as __START__
                       circle stop as __END__
                       usecase "evaluate_result"<<Node>>
                       usecase "agent_review"<<Node>>
                       hexagon "check state" as condition1<<Condition>>
                       "__START__" -down-> "evaluate_result"
                       "agent_review" -down-> "evaluate_result"
                       "evaluate_result" .down.> "condition1"
                       "condition1" .down.> "__END__": "OK"
                       '"evaluate_result" .down.> "__END__": "OK"
                       "condition1" .down.> "__END__": "UNKNOWN"
                       '"evaluate_result" .down.> "__END__": "UNKNOWN"
                       "condition1" .down.> "agent_review": "ERROR"
                       '"evaluate_result" .down.> "agent_review": "ERROR"
                       @enduml
                       """,
                result.content());

        // System.out.println( result.getContent() );


    }

    @Test
    public void GenerateAgentExecutorGraph() throws Exception {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("agent", this::dummyNodeAction)
                .addNode("action", this::dummyNodeAction)
                .addEdge(START, "agent")
                .addConditionalEdges(
                        "agent",
                        this::dummyCondition,
                        EdgeMappings.builder()
                                .to("action", "continue")
                                .toEND( "end" )
                                .build())
                .addEdge("action", "agent");

        CompiledGraph<AgentState> app = workflow.compile();

        GraphRepresentation result = app.getGraph(GraphRepresentation.Type.PLANTUML);
        assertEquals(GraphRepresentation.Type.PLANTUML, result.type());

        assertEquals("""
                        @startuml Graph_Diagram
                        skinparam usecaseFontSize 14
                        skinparam usecaseStereotypeFontSize 12
                        skinparam hexagonFontSize 14
                        skinparam hexagonStereotypeFontSize 12
                        title "Graph Diagram"
                        footer
                        powered by langgraph4j
                        end footer
                        circle start<<input>> as __START__
                        circle stop as __END__
                        usecase "agent"<<Node>>
                        usecase "action"<<Node>>
                        hexagon "check state" as condition1<<Condition>>
                        "__START__" -down-> "agent"
                        "agent" .down.> "condition1"
                        "condition1" .down.> "action": "continue"
                        '"agent" .down.> "action": "continue"
                        "condition1" .down.> "__END__": "end"
                        '"agent" .down.> "__END__": "end"
                        "action" -down-> "agent"
                        @enduml
                        """,
                result.content());

        // System.out.println( result.getContent() );
    }

    @Test
    public void GenerateImageToDiagramGraph() throws Exception {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("agent_describer", this::dummyNodeAction)
                .addNode("agent_sequence_plantuml", this::dummyNodeAction)
                .addNode("agent_generic_plantuml", this::dummyNodeAction)
                .addConditionalEdges(
                        "agent_describer",
                        this::dummyCondition,
                        EdgeMappings.builder()
                                .to( "agent_generic_plantuml", "generic" )
                                .to( "agent_sequence_plantuml", "sequence" )
                                .build())
                .addNode("evaluate_result", this::dummyNodeAction)
                .addEdge("agent_sequence_plantuml", "evaluate_result")
                .addEdge("agent_generic_plantuml", "evaluate_result")
                .addEdge(START, "agent_describer")
                .addEdge("evaluate_result", END);

        CompiledGraph<AgentState> app = workflow.compile();

        GraphRepresentation result = app.getGraph(GraphRepresentation.Type.PLANTUML);
        assertEquals(GraphRepresentation.Type.PLANTUML, result.type());

        assertEquals("""
                        @startuml Graph_Diagram
                        skinparam usecaseFontSize 14
                        skinparam usecaseStereotypeFontSize 12
                        skinparam hexagonFontSize 14
                        skinparam hexagonStereotypeFontSize 12
                        title "Graph Diagram"
                        footer
                        powered by langgraph4j
                        end footer
                        circle start<<input>> as __START__
                        circle stop as __END__
                        usecase "agent_describer"<<Node>>
                        usecase "agent_sequence_plantuml"<<Node>>
                        usecase "agent_generic_plantuml"<<Node>>
                        usecase "evaluate_result"<<Node>>
                        hexagon "check state" as condition1<<Condition>>
                        "__START__" -down-> "agent_describer"
                        "agent_describer" .down.> "condition1"
                        "condition1" .down.> "agent_generic_plantuml": "generic"
                        '"agent_describer" .down.> "agent_generic_plantuml": "generic"
                        "condition1" .down.> "agent_sequence_plantuml": "sequence"
                        '"agent_describer" .down.> "agent_sequence_plantuml": "sequence"
                        "agent_sequence_plantuml" -down-> "evaluate_result"
                        "agent_generic_plantuml" -down-> "evaluate_result"
                        "evaluate_result" -down-> "__END__"
                        @enduml
                        """,
                result.content());

        result = app.getGraph(GraphRepresentation.Type.MERMAID, "Graph Diagram", false);
        assertEquals(GraphRepresentation.Type.MERMAID, result.type());

        assertEquals("""
                ---
                title: Graph Diagram
                ---
                flowchart TD
                \t__START__((start))
                \t__END__((stop))
                \tagent_describer("agent_describer")
                \tagent_sequence_plantuml("agent_sequence_plantuml")
                \tagent_generic_plantuml("agent_generic_plantuml")
                \tevaluate_result("evaluate_result")
                \t%%	condition1{"check state"}
                \t__START__:::__START__ --> agent_describer:::agent_describer
                \t%%	agent_describer:::agent_describer -.-> condition1:::condition1
                \t%%	condition1:::condition1 -.->|generic| agent_generic_plantuml:::agent_generic_plantuml
                \tagent_describer:::agent_describer -.->|generic| agent_generic_plantuml:::agent_generic_plantuml
                \t%%	condition1:::condition1 -.->|sequence| agent_sequence_plantuml:::agent_sequence_plantuml
                \tagent_describer:::agent_describer -.->|sequence| agent_sequence_plantuml:::agent_sequence_plantuml
                \tagent_sequence_plantuml:::agent_sequence_plantuml --> evaluate_result:::evaluate_result
                \tagent_generic_plantuml:::agent_generic_plantuml --> evaluate_result:::evaluate_result
                \tevaluate_result:::evaluate_result --> __END__:::__END__
                
                \tclassDef __START__ fill:black,stroke-width:1px,font-size:xx-small;
                \tclassDef __END__ fill:black,stroke-width:1px,font-size:xx-small;
                """,
                result.content() );
    }

    private  AsyncNodeAction<MessagesState<String>> makeNode(String id ) {
        return node_async(state -> Map.of("messages", id) );
    }

    @Test
    void testWithParallelBranch() throws Exception {


        var workflow = new MessagesStateGraph<String>()
                .addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge("B", "C")
                .addEdge(START, "A")
                .addEdge("C", END);

        var result = workflow.getGraph(GraphRepresentation.Type.PLANTUML, "testWithParallelBranch");

        assertEquals("""
                @startuml testWithParallelBranch
                skinparam usecaseFontSize 14
                skinparam usecaseStereotypeFontSize 12
                skinparam hexagonFontSize 14
                skinparam hexagonStereotypeFontSize 12
                title "testWithParallelBranch"
                footer
                powered by langgraph4j
                end footer
                circle start<<input>> as __START__
                circle stop as __END__
                usecase "A"<<Node>>
                usecase "A1"<<Node>>
                usecase "A2"<<Node>>
                usecase "A3"<<Node>>
                usecase "B"<<Node>>
                usecase "C"<<Node>>
                "__START__" -down-> "A"
                "A" -down-> "A1"
                "A" -down-> "A2"
                "A" -down-> "A3"
                "A1" -down-> "B"
                "A2" -down-> "B"
                "A3" -down-> "B"
                "B" -down-> "C"
                "C" -down-> "__END__"
                @enduml
                """, result.content());

        result = workflow.getGraph(GraphRepresentation.Type.MERMAID, "testWithParallelBranch", false);

        assertEquals("""
                ---
                title: testWithParallelBranch
                ---
                flowchart TD
                	__START__((start))
                	__END__((stop))
                	A("A")
                	A1("A1")
                	A2("A2")
                	A3("A3")
                	B("B")
                	C("C")
                	__START__:::__START__ --> A:::A
                	A:::A --> A1:::A1
                	A:::A --> A2:::A2
                	A:::A --> A3:::A3
                	A1:::A1 --> B:::B
                	A2:::A2 --> B:::B
                	A3:::A3 --> B:::B
                	B:::B --> C:::C
                	C:::C --> __END__:::__END__
                
                	classDef __START__ fill:black,stroke-width:1px,font-size:xx-small;
                	classDef __END__ fill:black,stroke-width:1px,font-size:xx-small;
                """, result.content());
    }

    @Test
    void testWithParallelBranchOnStart() throws Exception {

        var workflow = new MessagesStateGraph<String>()
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge("B", "C")
                .addEdge(START, "A1")
                .addEdge(START, "A2")
                .addEdge(START, "A3")
                .addEdge("C", END);

        var result = workflow.compile().getGraph(GraphRepresentation.Type.PLANTUML, "testWithParallelBranchOnStart");

        assertEquals("""
@startuml testWithParallelBranchOnStart
skinparam usecaseFontSize 14
skinparam usecaseStereotypeFontSize 12
skinparam hexagonFontSize 14
skinparam hexagonStereotypeFontSize 12
title "testWithParallelBranchOnStart"
footer
powered by langgraph4j
end footer
circle start<<input>> as __START__
circle stop as __END__
usecase "A1"<<Node>>
usecase "A2"<<Node>>
usecase "A3"<<Node>>
usecase "B"<<Node>>
usecase "C"<<Node>>
"__START__" -down-> "A1"
"__START__" -down-> "A2"
"__START__" -down-> "A3"
"A1" -down-> "B"
"A2" -down-> "B"
"A3" -down-> "B"
"B" -down-> "C"
"C" -down-> "__END__"
@enduml
                """, result.content());

        result = workflow.getGraph(GraphRepresentation.Type.MERMAID, "testWithParallelBranchOnStart", false);
        System.out.println( result.content() );
        assertEquals("""
---
title: testWithParallelBranchOnStart
---
flowchart TD
	__START__((start))
	__END__((stop))
	A1("A1")
	A2("A2")
	A3("A3")
	B("B")
	C("C")
	__START__:::__START__ --> A1:::A1
	__START__:::__START__ --> A2:::A2
	__START__:::__START__ --> A3:::A3
	A1:::A1 --> B:::B
	A2:::A2 --> B:::B
	A3:::A3 --> B:::B
	B:::B --> C:::C
	C:::C --> __END__:::__END__

	classDef __START__ fill:black,stroke-width:1px,font-size:xx-small;
	classDef __END__ fill:black,stroke-width:1px,font-size:xx-small;
                """, result.content());
    }

    @Test
    public void issue216Test() throws Exception {
        var mockedAction = AsyncNodeAction.node_async((ignored) -> Map.of());

        var subSubGraph = new StateGraph<>(AgentState::new)
                .addNode("foo1", mockedAction)
                .addNode("foo2", mockedAction)
                .addNode("foo3", mockedAction)
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile()
                ;

        var subGraph = new StateGraph<>(AgentState::new)
                .addNode("bar1", mockedAction)
                .addNode("subGraph2", subSubGraph)
                .addNode("bar2", mockedAction)
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", "subGraph2")
                .addEdge("subGraph2", "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile()
                ;

        var stateGraph = new StateGraph<>(AgentState::new)
                .addNode("main1", mockedAction)
                .addNode("subgraph1", subGraph)
                .addNode("main2", mockedAction)
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", "main2")
                .addEdge("main2", StateGraph.END)
                ;

        var mermaid = stateGraph.getGraph(GraphRepresentation.Type.MERMAID, "Example graph", false);

        assertEquals("""
---
title: Example graph
---
flowchart TD
	__START__((start))
	__END__((stop))
	main1("main1")
subgraph subgraph1
	__START__subgraph1((start)):::__START__subgraph1
	__END__subgraph1((stop)):::__END__subgraph1
	bar1_subgraph1("bar1")
subgraph subGraph2
	__START__subGraph2((start)):::__START__subGraph2
	__END__subGraph2((stop)):::__END__subGraph2
	foo1_subGraph2("foo1")
	foo2_subGraph2("foo2")
	foo3_subGraph2("foo3")
	__START__subGraph2:::__START__subGraph2 --> foo1_subGraph2:::foo1_subGraph2
	foo1_subGraph2:::foo1_subGraph2 --> foo2_subGraph2:::foo2_subGraph2
	foo2_subGraph2:::foo2_subGraph2 --> foo3_subGraph2:::foo3_subGraph2
	foo3_subGraph2:::foo3_subGraph2 --> __END__subGraph2:::__END__subGraph2
end
	bar2_subgraph1("bar2")
	__START__subgraph1:::__START__subgraph1 --> bar1_subgraph1:::bar1_subgraph1
	bar1_subgraph1:::bar1_subgraph1 --> subGraph2:::subGraph2
	subGraph2:::subGraph2 --> bar2_subgraph1:::bar2_subgraph1
	bar2_subgraph1:::bar2_subgraph1 --> __END__subgraph1:::__END__subgraph1
end
	main2("main2")
	__START__:::__START__ --> main1:::main1
	main1:::main1 --> subgraph1:::subgraph1
	subgraph1:::subgraph1 --> main2:::main2
	main2:::main2 --> __END__:::__END__

	classDef __START__ fill:black,stroke-width:1px,font-size:xx-small;
	classDef __END__ fill:black,stroke-width:1px,font-size:xx-small;
                """, mermaid.content());

        var plantuml = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML, "Example graph", false);
        assertEquals("""
               @startuml Example_graph
               skinparam usecaseFontSize 14
               skinparam usecaseStereotypeFontSize 12
               skinparam hexagonFontSize 14
               skinparam hexagonStereotypeFontSize 12
               title "Example graph"
               footer
               powered by langgraph4j
               end footer
               circle start<<input>> as __START__
               circle stop as __END__
               usecase "main1"<<Node>>
               package subgraph1 {
               circle " " as subgraph1___START__
               circle exit as subgraph1___END__
               usecase "bar1"<<Node>> as subgraph1_bar1
               package subGraph2 {
               circle " " as subGraph2___START__
               circle exit as subGraph2___END__
               usecase "foo1"<<Node>> as subGraph2_foo1
               usecase "foo2"<<Node>> as subGraph2_foo2
               usecase "foo3"<<Node>> as subGraph2_foo3
               "subGraph2___START__" -down-> "subGraph2_foo1"
               "subGraph2_foo1" -down-> "subGraph2_foo2"
               "subGraph2_foo2" -down-> "subGraph2_foo3"
               "subGraph2_foo3" -down-> "subGraph2___END__"
               }
               usecase "bar2"<<Node>> as subgraph1_bar2
               "subgraph1___START__" -down-> "subgraph1_bar1"
               "subgraph1_bar1" -down-> "subGraph2"
               "subGraph2" -down-> "subgraph1_bar2"
               "subgraph1_bar2" -down-> "subgraph1___END__"
               }
               usecase "main2"<<Node>>
               "__START__" -down-> "main1"
               "main1" -down-> "subgraph1"
               "subgraph1" -down-> "main2"
               "main2" -down-> "__END__"
               @enduml
               """,
                plantuml.content()
        );

    }


    @Test
    public void issue300Test() throws Exception {
        AsyncNodeAction<AgentState> noop = AsyncNodeAction.node_async((ignored) -> Map.of());

        // Level 4 (deepest)
        StateGraph<AgentState> level4 = new StateGraph<>(AgentState::new);
        level4.addNode("L4_node", noop);
        level4.addEdge(StateGraph.START, "L4_node");
        level4.addEdge("L4_node", StateGraph.END);

        // Level 3: edge to level4 will be broken
        StateGraph<AgentState> level3 = new StateGraph<>(AgentState::new);
        level3.addNode("L3_node", noop);
        level3.addNode("level4", level4.compile());
        level3.addEdge(StateGraph.START, "L3_node");
        level3.addEdge("L3_node", "level4");
        level3.addEdge("level4", StateGraph.END);

        // Level 2
        StateGraph<AgentState> level2 = new StateGraph<>(AgentState::new);
        level2.addNode("level3", level3.compile());
        level2.addEdge(StateGraph.START, "level3");
        level2.addEdge("level3", StateGraph.END);

        // Level 1 (root)
        StateGraph<AgentState> root = new StateGraph<>(AgentState::new);
        root.addNode("level2", level2.compile());
        root.addEdge(StateGraph.START, "level2");
        root.addEdge("level2", StateGraph.END);

        var subGraphContext = DiagramGenerator.Context.builder()
                .title( "level2" )
                .printConditionalEdge( false )
                .isSubGraph( true )
                .build( root.nodes );

        assertTrue( subGraphContext.anySubGraphWithId("level3") );
        assertTrue( subGraphContext.anySubGraphWithId("level4") );

        var result = root.getGraph(GraphRepresentation.Type.MERMAID, "Minimal 4-Level Bug", false);

        assertEquals("""
                ---
                title: Minimal 4-Level Bug
                ---
                flowchart TD
                	__START__((start))
                	__END__((stop))
                subgraph level2
                	__START__level2((start)):::__START__level2
                	__END__level2((stop)):::__END__level2
                subgraph level3
                	__START__level3((start)):::__START__level3
                	__END__level3((stop)):::__END__level3
                	L3_node_level3("L3_node")
                subgraph level4
                	__START__level4((start)):::__START__level4
                	__END__level4((stop)):::__END__level4
                	L4_node_level4("L4_node")
                	__START__level4:::__START__level4 --> L4_node_level4:::L4_node_level4
                	L4_node_level4:::L4_node_level4 --> __END__level4:::__END__level4
                end
                	__START__level3:::__START__level3 --> L3_node_level3:::L3_node_level3
                	L3_node_level3:::L3_node_level3 --> level4:::level4
                	level4:::level4 --> __END__level3:::__END__level3
                end
                	__START__level2:::__START__level2 --> level3:::level3
                	level3:::level3 --> __END__level2:::__END__level2
                end
                	__START__:::__START__ --> level2:::level2
                	level2:::level2 --> __END__:::__END__
                
                	classDef __START__ fill:black,stroke-width:1px,font-size:xx-small;
                	classDef __END__ fill:black,stroke-width:1px,font-size:xx-small;
                """, result.content());
    }
}
