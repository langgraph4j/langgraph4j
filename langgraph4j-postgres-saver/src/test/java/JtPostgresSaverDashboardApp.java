//DEPS org.bsc.langgraph4j:langgraph4j-bom:1.9-SNAPSHOT@pom
//DEPS org.bsc.langgraph4j:langgraph4j-postgres-saver
//DEPS org.bsc.langgraph4j:langgraph4j-javelit

import io.javelit.core.Jt;
import io.javelit.core.JtContainer;
import org.bsc.javelit.JtDataTable;
import org.bsc.javelit.JtSessionValue;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.PostgresSaverV2Dashboard;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.bsc.langgraph4j.utils.TryFunction;

import java.util.List;
import java.util.Objects;

public class JtPostgresSaverDashboardApp {

    public static void main(String[] args) throws Exception {
        var app = new JtPostgresSaverDashboardApp();

        app.view("localhost", 5432, "postgres", "postgres", "lg4j-store_v2");
    }

    final StateSerializer<AgentState> JsonStateSerializer = new JacksonStateSerializer<AgentState>(AgentState::new) {
    };
    final StateSerializer<AgentState> binSateSerializer = new ObjectStreamStateSerializer<>(AgentState::new);

    final JtSessionValue<PostgresSaverV2Dashboard> saver$ = new JtSessionValue<>("saver");

    private PostgresSaverV2Dashboard initDashboard(String host,
                                                   int port,
                                                   String user,
                                                   String password,
                                                   String database) throws Exception {
        return PostgresSaverV2Dashboard.builder()
                .host(host)
                .port(port)
                .user(user)
                .password(password)
                .database(database)
                .stateSerializer(JsonStateSerializer)
                .stateSerializer(binSateSerializer)
                .build();
    }

    private void jtThreadsPanel(JtContainer container, PostgresSaverV2Dashboard saver) throws Exception {

        Jt.markdown("### THREADS")
                .use(container);

        final var threads = saver.selectAllThreads();

        final var selectedThreads = JtDataTable.builder(threads)
                .height("22vh")
                .singleSelection()
                .column("Id", v -> Objects.toString(v.id()))
                .column("Name", PostgresSaverV2Dashboard.ThreadRecord::name)
                .column("Is Interrupted", v -> Objects.toString(v.isInterrupted()))
                .column("Message", PostgresSaverV2Dashboard.ThreadRecord::message)
                .column("Created At", PostgresSaverV2Dashboard.ThreadRecord::createdAt)
                .use(container);

        if (selectedThreads == null || selectedThreads.isEmpty()) {
            return;
        }

        final var selectedThread = threads.get(selectedThreads.iterator().next());

        if (selectedThread == null) {
            return;
        }

        Jt.markdown("### CHECKPOINTS")
                .use(container);

        final var config = RunnableConfig.builder()
                .threadId(selectedThread.name())
                .build();

        final var checkpoints = saver.list(config).stream().toList();

        JtDataTable.builder(checkpoints)
                .height("44vh")
                .singleSelection()
                .column("nodeId", Checkpoint::getNodeId)
                .column("nextNodeId", Checkpoint::getNextNodeId)
                .column("state", v -> {
                    final var state = v.getState();
                    return CollectionsUtils.toString(state);
                })
                .use(container);
    }

    private void jtTagsPanel(JtContainer container, PostgresSaverV2Dashboard saver) throws Exception {

        Jt.markdown("### TAGS")
                .use(container);

        final var tags = saver.selectAllTags();

        final var selectedTags = JtDataTable.builder(tags)
                .height("22vh")
                .singleSelection()
                .column("Id", v -> Objects.toString(v.id()))
                .column("Name", PostgresSaverV2Dashboard.TagRecord::name)
                .column("Released Version", v -> Objects.toString(v.version()))
                .column("Is Error", v -> Objects.toString(v.isError()))
                .column("Is Released", v -> Objects.toString(v.isReleased()))
                .column("Message", PostgresSaverV2Dashboard.TagRecord::message)
                .column("Created At", PostgresSaverV2Dashboard.TagRecord::createdAt)
                .use(container);

        if (selectedTags == null || selectedTags.isEmpty()) {
            return;
        }

        final var selectedTag = tags.get(selectedTags.iterator().next());

        if (selectedTag == null) {
            return;
        }

        Jt.markdown("### CHECKPOINTS")
                .use(container);

        final var config = saver.addThreadRowId(
                RunnableConfig.builder()
                        .threadId(selectedTag.name())
                        .build(),
                selectedTag.id());

        final var checkpoints = saver.tag(config, selectedTag.version())
                .map(tag -> tag.checkpoints().stream().toList())
                .orElse(List.of());

        JtDataTable.builder(checkpoints)
                .height("44vh")
                .singleSelection()
                .column("nodeId", Checkpoint::getNodeId)
                .column("nextNodeId", Checkpoint::getNextNodeId)
                .column("state", v -> {
                    final var state = v.getState();
                    return CollectionsUtils.toString(state);
                })
                .use(container);
    }

    public void view(String host, int port, String user, String password, String database) throws Exception {
        Jt.title("Postgres Saver Dashboard App").use();

        final var tabs = Jt.tabs(List.of("Threads", "Tags")).use();

        final var THREADS_PANEL = tabs.tab("Threads");
        final var TAGS_PANEL = tabs.tab("Tags");

        final var saver = saver$.computeIfAbsent(TryFunction.Try(key ->
                initDashboard(host, port, user, password, database)));

        jtThreadsPanel(THREADS_PANEL, saver);
        jtTagsPanel(TAGS_PANEL, saver);
    }
}
