//DEPS org.bsc.langgraph4j:langgraph4j-studio:1.9-SNAPSHOT
//DEPS com.ag-ui.community:java-client:0.1.1

import com.agui.community.client.HttpAgent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.message.UserMessage;
import org.bsc.langgraph4j.studio.agui.AGUILangGraphStudioJacksonSerializer;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;

public class JbAGUIClientApp {

    static class EventEmitter implements Flow.Subscriber<Event> {

        private Flow.Subscription subscription;

        public EventEmitter() {
        }

        public Flow.Subscription subscription() {
            return subscription;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
            System.out.println( "Subscribed: " + subscription );
        }

        @Override
        public void onNext(Event event) {
            System.out.println( event );
        }

        @Override
        public void onError(Throwable throwable) {
            System.err.println( "Error: " + throwable.getMessage() );
            throwable.printStackTrace();
        }

        @Override
        public void onComplete() {
            System.out.println( "Completed" );
        }

    }
    public static void main(String[] args) throws  Exception {

        var app = new JbAGUIClientApp();

        app.run("base-agent");
    }

    public void run( String agent ) throws Exception {

        final var threadId = UUID.randomUUID().toString();
        final var runId = UUID.randomUUID().toString();

        final var client = new HttpAgent(
                new URI("http://localhost:8081/stream/%s".formatted(agent)),
                new AGUILangGraphStudioJacksonSerializer(),
                HttpClient.newHttpClient(),
                Runnable::run,
                Duration.ofSeconds(30));

        final var userMessage = new UserMessage(
                "user", "Hello, I need help with my project."
        );

        var input = new RunAgentInput(
                threadId,
                runId,
                null,
                List.of( userMessage ),
                List.of(), // tools
                List.of(), // context
                null // forwardedProps
        );

        client.run(input).subscribe(new EventEmitter() );

    }


}
