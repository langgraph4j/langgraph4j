//DEPS com.fasterxml.jackson.core:jackson-annotations:2.21
//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.9.0
//DEPS org.bsc.langgraph4j:langgraph4j-ag-ui-json:0.2.1
//DEPS com.ag-ui.community:java-client:0.1.1
//DEPS io.javelit:javelit:0.89.0

import com.agui.community.client.HttpAgent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.message.UserMessage;
import com.agui.json.AGUIJacksonSerializer;
import io.javelit.core.Jt;
import io.javelit.core.Server;
import org.bsc.javelit.JtSpinner;
import org.bsc.langgraph4j.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JtAGUIClientApp {

    static class EventEmitter implements Flow.Subscriber<Event> {

        private Flow.Subscription subscription;
        private final List<Event> receivedEvents = new ArrayList<>();
        private final CompletableFuture<List<Event>> future;

        public EventEmitter(CompletableFuture<List<Event>> future) {
            this.future = Objects.requireNonNull(future);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Event event) {
            receivedEvents.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            future.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            future.complete(receivedEvents);
        }

    }
    public static void main(String[] args) {

        var app = new JtAGUIClientApp();

        app.startServer();
    }
    void startServer() {
        // prepare a Javelit server
        var server = Server.builder(this::app, 8888).build();

        // start the server - this is non-blocking, user thread
        server.start();
    }

    public void app() {
        Jt.title("LangGraph4J AG UI Client").use();
        Jt.markdown("### Powered by LangGraph4j and SpringAI").use();

        final var threadId = UUID.randomUUID().toString();
        final var runId = UUID.randomUUID().toString();

        final var url = Jt.textInput("server url")
                .value("http://localhost:8081/sse/agent1")
                .use();

        var start = Jt.button("start agent")
                .disabled(url.isBlank())
                .use();

        if (start) {

            var spinner = JtSpinner.builder()
                    .message("**starting the agent** ....")
                    .use();

            try {
                final var startTime = Instant.now();

                final var client = new HttpAgent(new URI(url), new AGUIJacksonSerializer());

                final var userMessage = new UserMessage(
                        "m1", "Hello, I need help with my project."
                );

                var input = new RunAgentInput(
                        threadId,
                        runId,
                        null,
                        List.of( userMessage ),
                        List.of(), // tools
                        List.of(), // context
                        "props" // forwardedProps
                );

                final var future = new CompletableFuture<List<Event>>();

                client.run(input).subscribe(new EventEmitter(future));

                final var receivedEvents = future.get(1, TimeUnit.MINUTES);

                final var elapsedTime = Duration.between(startTime, Instant.now());

                final var events = receivedEvents.stream()
                        .map(Objects::toString)
                        .map("* %s"::formatted)
                        .collect(Collectors.joining("\n"));
                Jt.text(events).use();

                Jt.success("finished in %ds%n%n%s".formatted(elapsedTime.toSeconds(), "OK"))
                        .use(spinner);
            } catch (Exception e) {
                final var stackTrace = new StringWriter();
                e.printStackTrace(new PrintWriter(stackTrace));

                Jt.error("""
                        **Agent execution failed**

                        ```text
                        %s
                        ```
                        """.formatted(stackTrace.toString())).use(spinner);            }
        }

    }


}
