package org.bsc.langgraph4j.dsl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LangGraphDslVisualizerApplication.class)
@AutoConfigureMockMvc
class LangGraphDslVisualizerApplicationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void indexServesDslViewShell() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Langgraph4j DSL Visualizer")))
                .andExpect(content().string(containsString("<script type=\"module\" src=\"/lg4j-graph.js\"></script>")))
                .andExpect(content().string(containsString("<script type=\"module\" src=\"/lg4j-workbench.js\"></script>")))
                .andExpect(content().string(containsString("<script type=\"module\" src=\"/lg4j-result.js\"></script>")))
                .andExpect(content().string(containsString("<script type=\"module\" src=\"/lg4j-executor.js\"></script>")))
                .andExpect(content().string(containsString("<lg4j-workbench>")))
                .andExpect(content().string(containsString("<lg4j-graph slot=\"graph\"></lg4j-graph>")))
                .andExpect(content().string(containsString("<lg4j-result slot=\"result\"></lg4j-result>")))
                .andExpect(content().string(containsString("<lg4j-executor slot=\"executor\"></lg4j-executor>")));
    }

    @Test
    void dslViewServesWebComponentModule() throws Exception {
        mockMvc.perform(get("/lg4j-graph.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("@xyflow/react")))
                .andExpect(content().string(containsString("react@19")))
                .andExpect(content().string(containsString("export class LG4JDSLViewElement extends HTMLElement")))
                .andExpect(content().string(containsString("customElements.define('lg4j-graph', LG4JDSLViewElement)")))
                .andExpect(content().string(containsString("CircleNode")))
                .andExpect(content().string(containsString("NodeResizer")))
                .andExpect(content().string(containsString("SubgraphNode")))
                .andExpect(content().string(containsString("rewriteSubgraphBoundaryEdges")))
                .andExpect(content().string(containsString("collapsedSubgraphs")))
                .andExpect(content().string(containsString("draggable: true")))
                .andExpect(content().string(containsString("extent: node.parentId ? 'parent' : node.extent")))
                .andExpect(content().string(containsString("event.stopPropagation()")))
                .andExpect(content().string(containsString("Position.Top")))
                .andExpect(content().string(containsString("Position.Bottom")))
                .andExpect(content().string(containsString("position: data.collapsed ? Position.Top : Position.Left")))
                .andExpect(content().string(containsString("position: data.collapsed ? Position.Bottom : Position.Right")))
                .andExpect(content().string(containsString("autoLayoutNodes")))
                .andExpect(content().string(containsString("rankGroupNodes")))
                .andExpect(content().string(containsString("savedPositionsRef")))
                .andExpect(content().string(containsString("savedSizesRef")))
                .andExpect(content().string(containsString("handleNodesChange")))
                .andExpect(content().string(containsString("onResizeEnd")))
                .andExpect(content().string(containsString("savedSizes.set(node.id")))
                .andExpect(content().string(containsString("this.addEventListener('graph', this.render)")))
                .andExpect(content().string(containsString("this.addEventListener('graph-active', this.onActive)")))
                .andExpect(content().string(containsString("source: this.source")))
                .andExpect(content().string(containsString("activeNodeId: this.activeNodeId")))
                .andExpect(content().string(containsString("@keyframes lg4j-spin")));
    }

    @Test
    void workbenchServesLayoutAndEventRouterWebComponentModule() throws Exception {
        mockMvc.perform(get("/lg4j-workbench.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("export class LG4JWorkbenchElement extends HTMLElement")))
                .andExpect(content().string(containsString("grid-template-columns: minmax(0, 1fr) minmax(280px, 28vw)")))
                .andExpect(content().string(containsString("grid-template-rows: minmax(0, 1fr) minmax(180px, 32vh)")))
                .andExpect(content().string(containsString("<slot name=\"graph\"></slot>")))
                .andExpect(content().string(containsString("<slot name=\"result\"></slot>")))
                .andExpect(content().string(containsString("<slot name=\"executor\"></slot>")))
                .andExpect(content().string(containsString("this.addEventListener('graph', this.forwardGraph)")))
                .andExpect(content().string(containsString("this.addEventListener('graph-active', this.forwardGraphActive)")))
                .andExpect(content().string(containsString("this.addEventListener('graph-acive', this.forwardGraphActive)")))
                .andExpect(content().string(containsString("this.resultElement?.dispatchEvent(new CustomEvent(type, { detail }))")))
                .andExpect(content().string(containsString("customElements.define('lg4j-workbench', LG4JWorkbenchElement)")));
    }

    @Test
    void executorServesBackendAndGraphEventWebComponentModule() throws Exception {
        mockMvc.perform(get("/lg4j-executor.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("export class LG4JExecutorElement extends HTMLElement")))
                .andExpect(content().string(containsString("fetch('/api/graph')")))
                .andExpect(content().string(containsString("input id=\"active-node\"")))
                .andExpect(content().string(containsString("this.dispatchGraphEvent('graph', source)")))
                .andExpect(content().string(containsString("this.dispatchGraphEvent('graph-active'")))
                .andExpect(content().string(containsString("bubbles: true")))
                .andExpect(content().string(containsString("composed: true")))
                .andExpect(content().string(containsString("customElements.define('lg4j-executor', LG4JExecutorElement)")));
    }

    @Test
    void resultServesDslJsonPanelWebComponentModule() throws Exception {
        mockMvc.perform(get("/lg4j-result.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("export class LG4JResultElement extends HTMLElement")))
                .andExpect(content().string(containsString("textarea id=\"dsl-source\" readonly")))
                .andExpect(content().string(containsString("this.addEventListener('graph', this.renderGraph)")))
                .andExpect(content().string(containsString("JSON.stringify(JSON.parse(source), null, 2)")))
                .andExpect(content().string(containsString("customElements.define('lg4j-result', LG4JResultElement)")));
    }

    @Test
    void graphEndpointReturnsGeneratedLangGraphDsl() throws Exception {
        mockMvc.perform(get("/api/graph"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.type").value("langgraph4j"))
                .andExpect(jsonPath("$.version").value("1.0"))
                .andExpect(jsonPath("$.nodes[?(@.id == 'planner')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == 'tool_executor')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == 'tool_executor-call_tool')]").exists())
                .andExpect(jsonPath("$.edges[?(@.label == 'tool')]").exists())
                .andExpect(jsonPath("$.subgraphs[?(@.id == 'tool_executor')]").exists());
    }
}
