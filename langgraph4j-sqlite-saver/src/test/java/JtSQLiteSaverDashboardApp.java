//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.4
//DEPS org.bsc.langgraph4j:langgraph4j-bom:1.9-SNAPSHOT@pom
//DEPS org.bsc.langgraph4j:langgraph4j-sqlite-saver
//DEPS org.bsc.langgraph4j:langgraph4j-javelit
//DEPS org.bsc.langgraph4j:langgraph4j-ag-ui-json
//DEPS com.ag-ui.community:java-client:0.1.1
//DEPS io.javelit:javelit:0.89.0

import com.agui.community.client.HttpAgent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.*;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.Resume;
import com.agui.community.core.interrupt.ResumeStatus;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.json.AGUIJacksonSerializer;
import io.javelit.components.layout.ColumnsComponent;
import io.javelit.core.Jt;
import io.javelit.core.JtContainer;
import io.javelit.core.Server;
import org.bsc.javelit.*;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.SQLiteSaverV2Dashboard;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.bsc.langgraph4j.utils.TryFunction;
import org.jspecify.annotations.NonNull;
import org.sqlite.SQLiteConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public class JtSQLiteSaverDashboardApp {


    public static void main(String[] args) throws Exception {

        var app = new JtSQLiteSaverDashboardApp();

        app.startServer();
    }

    final WorkflowManager workflowManager;
    final CheckpointDashboard checkpointDashboard;


    JtSQLiteSaverDashboardApp() throws Exception {
        workflowManager = new WorkflowManager();
        checkpointDashboard = new CheckpointDashboard();
    }

    void startServer() {
        // prepare a Javelit server
        var server = Server.builder(this::app, 8888).build();

        // start the server - this is non-blocking, user thread
        server.start();
    }

    public void app () throws Exception {

        Jt.title("SQLite Saver Dashboard").use();

        var page = Jt.navigation(
                        Jt.page("Dashboard", checkpointDashboard::show)
                                .title("Checkpoint Dashboard").home(),
                        Jt.page("run-agent", workflowManager::show)
                                .title("Run Agent")
                        )
                .use();
        page.run();
    }

}

class CheckpointDashboard {

    final JtSessionValue<SQLiteSaverV2Dashboard> service$ = new JtSessionValue<>("saverService");
    final JtCacheValue<RunAgentInput> agentInput$ = new JtCacheValue<>("agent-input");

    final StateSerializer<AgentState> JsonStateSerializer = new JacksonStateSerializer<AgentState>(AgentState::new) {};
    final StateSerializer<AgentState> binSateSerializer = new ObjectStreamStateSerializer<>(AgentState::new);

    public CheckpointDashboard()  {}

    private SQLiteSaverV2Dashboard initDashboard(Path databasePath) throws Exception {
        final var sqlConfig = new SQLiteConfig();
        sqlConfig.setReadOnly(true);

        return SQLiteSaverV2Dashboard.builder()
                .databasePath(databasePath.toString())
                .config(sqlConfig)
                .stateSerializer(JsonStateSerializer)
                .stateSerializer(binSateSerializer)
                .build();
    }

    public void show() throws Exception {

        final var databasePath = Path.of(System.getProperty("user.home"), ".langgraph4j", "SQLiteSaverTest.db");

        final var tabs = Jt.tabs(List.of("Threads", "Tags")).use();

        final var THREADS_PANEL = tabs.tab("Threads");
        final var TAGS_PANEL = tabs.tab("Tags");

        final var service = service$.computeIfAbsent(TryFunction.Try(key ->
                initDashboard(databasePath)));

        threadsPanel(THREADS_PANEL, service);
        tagsPanel(TAGS_PANEL, service);

    }

    private void threadsPanel(JtContainer container, SQLiteSaverV2Dashboard service) throws Exception {

        Jt.markdown("### THREADS")
                .use(container);

        final var threads = service.selectAllThreads();

        final var selectedThreads = JtDataTable.builder(threads)
                .height("22vh")
                .singleSelection()
                .column("Id", v -> Objects.toString(v.id()))
                .column("Name", SQLiteSaverV2Dashboard.ThreadRecord::name)
                .column("Is Interrupted", v -> Objects.toString(v.isInterrupted()))
                .column("Message", SQLiteSaverV2Dashboard.ThreadRecord::message)
                .column("Created At", SQLiteSaverV2Dashboard.ThreadRecord::createdAt)
                .use(container);

        if (selectedThreads == null || selectedThreads.isEmpty()) {
            return;
        }

        final var selectedThread = threads.get(selectedThreads.iterator().next());

        if (selectedThread == null) {
            return;
        }

        final var resumeThread = Jt.button("Resume %s".formatted(selectedThread.name()))
                .disabled(!selectedThread.isInterrupted())
                .use(container);
        if( resumeThread ) {
            final var resume = new Resume( "interruption", ResumeStatus.RESOLVED, null);
            agentInput$.setValue(new RunAgentInput(
                    selectedThread.name(),
                    UUID.randomUUID().toString(),
                    Map.of(),
                    List.of(), // messages
                    List.of(), // tools
                    List.of(), // context
                    null, // forwardedProps,
                    List.of(resume)
            ));
            System.out.println("### AGENT INPUT ###" + Jt.cache().get("agent-input"));
            Jt.switchPage("/run-agent");
        }


        Jt.markdown("### CHECKPOINTS")
                .use(container);

        final var config = RunnableConfig.builder()
                .threadId(selectedThread.name())
                .build();

        final var checkpoints = service.list(config).stream().toList();

        final var selectCheckpoints = JtDataTable.builder(checkpoints)
                .height("44vh")
                .singleSelection()
                .column("nodeId", Checkpoint::nodeId)
                .column("nextNodeId", Checkpoint::nextNodeId)
                .column("state", v -> {
                    final var state = v.state();
                    return CollectionsUtils.toString(state);
                })
                .use(container);
    }

    private void tagsPanel(JtContainer container, SQLiteSaverV2Dashboard service) throws Exception {

        Jt.markdown("### TAGS")
                .use(container);

        final var tags = service.selectAllTags();

        final var selectedTags = JtDataTable.builder(tags)
                .height("22vh")
                .singleSelection()
                .column("Id", v -> Objects.toString(v.id()))
                .column("Name", SQLiteSaverV2Dashboard.TagRecord::name)
                .column("Released Version", v -> Objects.toString(v.version()))
                .column("Is Error", v -> Objects.toString(v.isError()))
                .column("Is Released", v -> Objects.toString(v.isReleased()))
                .column("Message", SQLiteSaverV2Dashboard.TagRecord::message)
                .column("Created At", SQLiteSaverV2Dashboard.TagRecord::createdAt)
                .use(container);

        if( selectedTags == null || selectedTags.isEmpty() ) {
            return;
        }

        final var selectedTag = tags.get(selectedTags.iterator().next());

        if( selectedTag == null ) {
            return;
        }
        
        Jt.markdown("### CHECKPOINTS")
                .use(container);

        final var config = RunnableConfig.builder()
                .threadId(selectedTag.name())
                .build();

        final var checkpoints = service.tag(config, selectedTag.version())
                .map(tag -> tag.checkpoints().stream().toList())
                .orElse(List.of());

        final var selectCheckpoints = JtDataTable.builder(checkpoints)
                .height("22vh")
                .singleSelection()
                .column("nodeId", Checkpoint::nodeId)
                .column("nextNodeId", Checkpoint::nextNodeId)
                .column("state", v -> {
                    final var state = v.state();
                    return CollectionsUtils.toString(state);
                })
                .use(container);

        final var isSelectedCheckpoint = !selectedTags.isEmpty() && selectCheckpoints != null && !selectCheckpoints.isEmpty();
        final var jsonEditorPanel = Jt.popover("Edit Checkpoint State & Resume Agent")
                .disabled( !isSelectedCheckpoint)
                .use(container);

        if( isSelectedCheckpoint ) {

            final var index = selectCheckpoints.iterator().next();
            final var selectedCheckpoint = checkpoints.get(index);

            final var newState = JtJsonEditor.builder()
                    .width(500)
                    .json(JsonStateSerializer.writeDataAsString(selectedCheckpoint.state()))
                    .schema("""
                            {
                              "title": "State",
                              "type": "object",
                              "properties": {
                                "messages": {"type": "array", "minimum": 0}
                              },
                              "required": ["messages"]
                            }
                            """)
                    .use(jsonEditorPanel);
            final var resumeAgent = Jt.button( "Resume Agent" )
                    .use(jsonEditorPanel);

            if( resumeAgent ) {

                final var resume = new Resume("checkpoints", ResumeStatus.RESOLVED, """
                        [{
                            "@type": "%s",
                            "id": "%s",
                            "state": %s,
                            "nodeId": "%s",
                            "nextNodeId": "%s"
                        }]
                        """.formatted(
                                Checkpoint.class.getName(),
                                selectedCheckpoint.id(),
                                newState,
                                selectedCheckpoint.nodeId(),
                                selectedCheckpoint.nextNodeId()
                        ));

                agentInput$.setValue(new RunAgentInput(
                        selectedTag.name(),
                        UUID.randomUUID().toString(),
                        Map.of(),
                        List.of(), // messages
                        List.of(), // tools
                        List.of(), // context
                        null, // forwardedProps,
                        List.of(resume)
                ));
                Jt.switchPage("/run-agent");
            }
        }
    }
}


class WorkflowManager {
    final List<String> availableAgents = List.of("agent-hitl", "agent-error");

    final com.agui.community.core.serialization.Serializer agUiSerializer = new AGUIJacksonSerializer();

    final JtCacheValue<RunAgentInput> agentInput$ = new JtCacheValue<>("agent-input");

    public WorkflowManager(){
    }

    private HttpAgent newClient(String agentName) throws URISyntaxException {
        return new HttpAgent(
                new URI("http://localhost:8080/sse/%s".formatted(agentName)),
                agUiSerializer,
                java.net.http.HttpClient.newHttpClient(),
                Runnable::run, // single threaded executor
                java.time.Duration.ofMinutes(5)
        );
    }

    private void processEvent(Event event, JtContainer eventContainer) {

        switch (event.type()) {
            case RUN_STARTED: {
                final var e = (RunStartedEvent) event;
                Jt.text("Agent thread %s started".formatted(e.threadId()))
                        .use(eventContainer);
            }
            break;
            case RUN_FINISHED: {
                final var e = (RunFinishedEvent) event;
                if( e.outcome() instanceof SuccessOutcome outcome) {
                    Jt.success("Agent thread %s finished successfully".formatted(e.threadId()))
                            .use(eventContainer);
                }
                if( e.outcome() instanceof InterruptOutcome outcome) {
                    if( outcome.interrupts().isEmpty() ) {
                        Jt.warning("Agent thread %s interruped without any interrupt metadata".formatted(e.threadId()))
                                .use(eventContainer);
                    }
                    else if( outcome.interrupts().size() > 1 ) {
                        Jt.warning("Agent thread %s interruped with multiple interrupt metadata that is not supported yet".formatted(e.threadId()))
                                .use(eventContainer);
                    }
                    else {
                        final var interruption = outcome.interrupts().get(0);

                        Jt.warning("Agent thread %s interruped on %s".formatted(e.threadId(), interruption.id()))
                                .use(eventContainer);
                    }
                }
            }
            break;
            case RUN_ERROR: {
                final var e = (RunErrorEvent) event;
                Jt.text("Agent error %s message: %s".formatted(e.code(), e.message()))
                        .use(eventContainer);
            }
            break;
            case STEP_STARTED: {
                final var e = (StepStartedEvent) event;
                Jt.text( "%s started".formatted(e.stepName()))
                        .use(eventContainer);
            }
            break;
            case STEP_FINISHED: {
                final var e = (StepFinishedEvent) event;
                Jt.text("%s finished".formatted(e.stepName()))
                        .use(eventContainer);
            }
            break;
            case TEXT_MESSAGE_CHUNK: {
                final var e = (TextMessageChunkEvent) event;
                Jt.text(e.delta())
                        .use(eventContainer);
            }
            break;
            case TEXT_MESSAGE_CONTENT: {
                final var e = (TextMessageContentEvent) event;
                Jt.text(e.delta())
                        .use(eventContainer);
            }
            break;
            default:
                break;
        }

    }

    public void show() {

        Jt.markdown("### WORKFLOW MANAGER").use();

        var agentInput = agentInput$.value().orElse(null);

        System.out.println("### AGENT INPUT ###" + Jt.cache().get("agent-input"));

        final ThreadId threadId;

        if( agentInput != null ) {
            threadId = ThreadId.parse(agentInput.threadId());
        } else {
            threadId = ThreadId.of("<none>", "thread1");
        }

        final var c1 = Jt.columns(2)
                .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
                .gap(ColumnsComponent.Gap.SMALL)
                .use();
        final var c2 = Jt.columns(2)
                .widths( List.of( .10,.10))
                .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
                .gap(ColumnsComponent.Gap.NONE)
                .use();


        final var defaultAgent = Math.max(availableAgents.indexOf(threadId.agentName()), 0);
        final var agentName = Jt.selectbox("Available Agents", availableAgents)
                .index(defaultAgent)
                .disabled(agentInput != null)
                .use(c1.col(0));
        final var threadName = Jt.textInput("Thread Name")
                .value(threadId.threadName())
                .disabled(agentInput != null)
                .use(c1.col(1));

        final var isResume = ( agentInput != null && !agentInput.resume().isEmpty());

        final var runAgent = Jt.button(isResume ? "Resume Agent" : "Run Agent")
                .use(c2.col(0));
        final var dismiss = Jt.button("Dismiss")
                .use(c2.col(1));

        Jt.divider().use();

        if( dismiss ) {
            agentInput$.clear();
            Jt.switchPage(null);
        }
        if( runAgent ) {
            final  var jtSpinner = JtSpinner.builder()
                    .message("**starting the agent** ....")
                    .key("spinner")
                    .use();


            final var jtExpanderEvents = Jt.expander("Process Events (logs)")
                    .key("events")
                    .use();
            final var startTime = Instant.now();


            if( agentInput == null ) {
                agentInput = new RunAgentInput(
                        ThreadId.of(agentName,threadName).toString(),
                        UUID.randomUUID().toString(),
                        Map.of(),
                        List.of(), // messages
                        List.of(), // tools
                        List.of(), // context
                        null // forwardedProps
                );
            }


            runAgent(agentInput, jtExpanderEvents)
                    .thenAccept( v -> {
                        final var elapsedTime = Duration.between(startTime, Instant.now());
                        Jt.success("Agent run completed in %ds%n%n".formatted(elapsedTime.toSeconds()))
                                .use(jtSpinner);
                    })
                    .whenComplete( (v, e) -> {
                        agentInput$.clear();
                    })
                    .exceptionally( e -> {
                        Jt.error("Agent run failed: %s".formatted(e.getMessage()))
                                .use(jtSpinner);
                        return null;
                    })
                    .join();
        }

    }

    CompletableFuture<Void> runAgent(RunAgentInput input, JtContainer eventsContainer) {
        final var future = new CompletableFuture<Void>();

        ThreadId threadId = ThreadId.parse(input.threadId());
        try {
            newClient(threadId.agentName()).run(input).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(Event event) {
                    processEvent(event, eventsContainer);
                }

                @Override
                public void onError(Throwable throwable) {
                    future.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    future.complete(null);
                }
            });

        }
        catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}

record ThreadId(String agentName, String threadName) {

    static ThreadId parse(String threadId) {
        final var parts = threadId.split("/");
        if(parts.length != 2) {
            throw new IllegalArgumentException("Invalid threadId format. Expected format: 'agentName/threadName'");
        }
        return new ThreadId(parts[0], parts[1]);
    }

    static ThreadId of(String agentName, String threadName) {
        return new ThreadId(agentName, threadName);
    }

    public ThreadId {
        if( Objects.requireNonNull(agentName, "agentName cannot be null").isBlank() ) {;
            throw new IllegalArgumentException("agentName cannot be null or blank");
        }
        if( Objects.requireNonNull(threadName, "threadName cannot be null").isBlank() ) {
            throw new IllegalArgumentException("threadName cannot be null or blank");
        }
    }

    @Override
    public String toString() {
        return "%s/%s".formatted(agentName, threadName);
    }

}


