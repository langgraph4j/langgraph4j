package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.event.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Flow;

/**
 * Spring Boot controller exposing a Server-Sent Events (SSE) endpoint compliant with the
 * AG-UI protocol, driving the lifecycle of a registered LangGraph4j {@link AGUIAgent}.
 * <p>
 * A single {@code POST /sse/{agentId}} endpoint deserializes the incoming request body into
 * an {@link AGUIRunAgentInput}, looks up the target agent from the {@link AGUIAgentRegistry},
 * starts its run, and streams each produced AG-UI {@link Event} back to the client as a
 * {@code text/event-stream} response, using an {@link SseEmitter} bridged to the agent's
 * reactive {@link Flow.Publisher}. The connection is kept open until the agent run completes
 * or fails, at which point the emitter is completed (successfully or with the corresponding
 * error).
 */
@Controller
public class AGUISSEController {

    private final AGUIAgentRegistry agents;
    private final com.agui.community.core.serialization.Serializer aguiSerializer;

    /**
     * Creates a new controller.
     *
     * @param agents         the registry used to resolve an agent by id
     * @param aguiSerializer the serializer used to (de)serialize AG-UI requests/events to/from JSON
     */
    public AGUISSEController(AGUIAgentRegistry agents, com.agui.community.core.serialization.Serializer aguiSerializer) {
        this.agents = agents;
        this.aguiSerializer = aguiSerializer;
    }

    /**
     * Starts (or resumes) a run of the agent identified by {@code agentId} and streams back
     * the AG-UI events it produces as a Server-Sent Events response.
     * <p>
     * The request body is expected to be the JSON representation of an {@link AGUIRunAgentInput}.
     * Each event emitted by the agent's {@link Flow.Publisher} is serialized to JSON and sent to
     * the client as a named {@code "event"} SSE event; the emitter is completed when the agent
     * run finishes, or completed with the corresponding error if the run or the event
     * serialization/delivery fails.
     *
     * @param agentId the identifier of the agent to run, as registered in the {@link AGUIAgentRegistry}
     * @param params  the JSON body describing the run request (see {@link AGUIRunAgentInput})
     * @return an SSE response entity carrying the {@link SseEmitter} that will stream the AG-UI events
     * @throws JsonProcessingException if the request body cannot be parsed
     * @throws IllegalArgumentException if no agent is registered with the given {@code agentId}
     */
    @PostMapping(
            value = "/sse/{agentId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamDataWithSseEmitter(
            @PathVariable("agentId") String agentId,
            @RequestBody String params
    ) throws JsonProcessingException {

        final var emitter = new SseEmitter(0L);
        final var parameters = aguiSerializer.deserialize(params, AGUIRunAgentInput.class);

        final var agent = agents.agent(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent '%s' not found".formatted(agentId)));

        agent.run(parameters.toRunAgentParameters())
                .subscribe(new Flow.Subscriber<Event>() {
                               @Override
                               public void onSubscribe(Flow.Subscription subscription) {
                                      subscription.request(Long.MAX_VALUE);
                               }

                               @Override
                               public void onNext(Event event) {
                                   try {
                                       final var data = aguiSerializer.serialize(event);
                                       emitter.send(
                                               SseEmitter.event()
                                                       .name("event")
                                                       .data(data, MediaType.APPLICATION_JSON)
                                       );
                                   } catch (Exception e) {
                                       emitter.completeWithError(e);
                                   }

                               }

                               @Override
                               public void onError(Throwable throwable) {
                                    emitter.completeWithError(throwable);
                               }

                               @Override
                               public void onComplete() {
                                      emitter.complete();
                               }
                           });

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    /*
    @PostMapping(value = "/flux/{agentId}")
    public Flux<String> streamDataWithFlux(@PathVariable("agentId") final String agentId, @RequestBody() String  params ) throws JsonProcessingException {

        final var parameters = aguiSerializer.deserialize(params, AGUIRunAgentInput.class);

        return JdkFlowAdapter.flowPublisherToFlux(agent.run(parameters.toRunAgentParameters()))
                    .subscribeOn(Schedulers.immediate());
                    .map( event -> {
                        try {
                            final var json = aguiSerializer.serialize(event);
                            return " %s".formatted(json);
                        } catch (Exception e) {
                            throw new Error( e );
                        }
                    });
        }
    */
}
