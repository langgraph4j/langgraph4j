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

@Controller
public class AGUISSEController {

    private final AGUIAgentRegistry agents;
    private final com.agui.community.core.serialization.Serializer aguiSerializer;

    public AGUISSEController(AGUIAgentRegistry agents, com.agui.community.core.serialization.Serializer aguiSerializer) {
        this.agents = agents;
        this.aguiSerializer = aguiSerializer;
    }

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
