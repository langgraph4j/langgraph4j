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
                .andExpect(content().string(containsString("<script type=\"module\" src=\"/dsl-view.js\"></script>")))
                .andExpect(content().string(containsString("fetch('/api/graph')")))
                .andExpect(content().string(containsString("new CustomEvent('graph', { detail: source })")))
                .andExpect(content().string(containsString("<lg4j-dsl-view></lg4j-dsl-view>")));
    }

    @Test
    void dslViewServesWebComponentModule() throws Exception {
        mockMvc.perform(get("/dsl-view.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("@xyflow/react")))
                .andExpect(content().string(containsString("react@19")))
                .andExpect(content().string(containsString("export class LG4JDSLViewElement extends HTMLElement")))
                .andExpect(content().string(containsString("customElements.define('lg4j-dsl-view', LG4JDSLViewElement)")))
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
                .andExpect(content().string(containsString("source: event.detail")));
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
