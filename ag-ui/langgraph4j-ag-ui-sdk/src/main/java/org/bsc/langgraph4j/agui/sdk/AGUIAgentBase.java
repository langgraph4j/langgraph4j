package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.*;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.community.core.message.Role;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.utils.ExceptionUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Base implementation of {@link AGUIAgent} that bridges a langgraph4j {@link CompiledGraph}
 * execution with the AG-UI protocol event stream.
 * <p>
 * Subclasses provide the actual graph ({@link #newGraph()}) and how to translate an
 * incoming {@link RunAgentInput} into graph input ({@link #graphInput(RunAgentInput)}).
 * This base class takes care of lazily compiling the graph, running it (either
 * asynchronously or synchronously), and converting node/streaming outputs, errors and
 * completion into the corresponding AG-UI {@link Event}s.
 */
public abstract class AGUIAgentBase implements AGUIAgent {

    private final String id;
    protected CompiledGraph<? extends AgentState> graph; // Lazy initialized
    private final AtomicReference<String> streamingId = new AtomicReference<>();

    /**
     * Creates a new agent base with the given identifier.
     *
     * @param id the unique identifier of this agent; must not be {@code null}
     */
    public AGUIAgentBase(String id) {
        this.id = requireNonNull(id, "id must not be null");
    }

    /**
     * Returns the unique identifier of this agent.
     *
     * @return the agent id
     */
    @Override
    public String id() {
        return id;
    }

    /**
     * Creates a new compiled graph instance to be used by this agent.
     * <p>
     * The graph is lazily created on the first {@link #run(RunAgentInput)} invocation
     * and cached for subsequent invocations.
     *
     * @return the newly compiled graph
     * @throws Exception if the graph cannot be built/compiled
     */
    abstract protected CompiledGraph<? extends AgentState> newGraph() throws Exception;

    /**
     * Builds the graph input to be used for a given agent run request.
     *
     * @param input the incoming run request
     * @return the input to feed to the underlying graph execution
     */
    abstract protected GraphInput graphInput(RunAgentInput input);

    /**
     * Generates a new unique message identifier.
     * <p>
     * The default implementation returns the current system time in milliseconds
     * as a string.
     *
     * @return a newly generated message id
     */
    protected String newMessageId() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * Builds the {@link RunnableConfig} used to execute the graph for the given run request.
     *
     * @param input the incoming run request
     * @return the runnable configuration, bound to the request's thread id
     */
    protected RunnableConfig runnableConfig(RunAgentInput input) {
        return RunnableConfig.builder()
                .threadId(input.threadId())
                .build();
    }

    /**
     * Translates a graph node output into the collection of AG-UI events to be emitted
     * to the subscriber.
     *
     * @param input  the original run request
     * @param output the node output produced by the graph
     * @return the events to emit for this output, or an empty collection if the output
     *         does not carry any AG-UI event
     */
    protected Collection<? extends Event> onNextEvents(RunAgentInput input, NodeOutput<? extends AgentState> output) {

        if (output instanceof AGUINodeOutput<? extends AgentState> aguiNodeOutput) {
            return aguiNodeOutput.events();
        }
        return List.of();
    }

    /**
     * Translates a graph execution error into the collection of AG-UI events to be emitted
     * to the subscriber.
     *
     * @param input the original run request
     * @param error the error raised during graph execution
     * @return the events to emit describing the error, typically a single {@link RunErrorEvent}
     */
    protected Collection<? extends Event> onErrorEvents(RunAgentInput input, Throwable error) {

        final var msg = ExceptionUtils.findCauseByType(error, GraphRunnerException.class)
                .map(Throwable::getMessage)
                .orElseGet(error::getMessage);
        return List.of(new RunErrorEvent(msg));
    }

    /**
     * Translates a successful graph execution completion into the collection of AG-UI
     * events to be emitted to the subscriber.
     *
     * @param input  the original run request
     * @param result the result produced by the completed graph execution
     * @return the events to emit for the completion, typically a single {@link RunFinishedEvent}
     */
    protected Collection<? extends Event> onCompleteEvents(RunAgentInput input, GraphResult result) {
        return List.of(new RunFinishedEvent(
                input.threadId(),
                input.runId(),
                new SuccessOutcome(),
                null,
                System.currentTimeMillis(),
                null));
    }

    /**
     * Notifies the graph's checkpoint saver, if any, that the execution associated with
     * the given run request failed, allowing it to release any held resources.
     *
     * @param input the original run request
     * @param error the error that caused the execution to fail
     * @throws Exception if the checkpoint saver fails to release resources on error
     */
    private void releaseOnError(RunAgentInput input, Throwable error) throws Exception {
        log.error("Error during graph execution", error);
        if (graph.compileConfig.checkpointSaver().isPresent()) {
            try {
                final var runnableConfig = this.runnableConfig(input);
                graph.compileConfig.checkpointSaver().get().releaseOnError(runnableConfig, error);
            } catch (Exception e) {
                log.error("Error releasing graph execution on error", e);
                throw e;
            }
        }

    }

    /**
     * Starts an asynchronous execution of this agent for the given run request, publishing
     * a {@link RunStartedEvent} before delegating the actual graph execution to
     * {@link #runAsync(Flow.Subscriber, RunAgentInput)}.
     * <p>
     * The underlying graph is lazily created on first use via {@link #newGraph()}.
     *
     * @param input the run request describing the execution to perform
     * @return a publisher that emits the AG-UI events produced by this run
     */
    @Override
    public Flow.Publisher<? extends Event> run(RunAgentInput input) {
        return ( subscriber ) -> {

            if (graph == null) {
                try {
                    graph = newGraph(); // Lazy creation of the graph
                } catch (Exception e) {
                    subscriber.onError(e);
                    return;
                }
            }

            subscriber.onNext(new RunStartedEvent(
                    input.threadId(),
                    input.runId(),
                    null,
                    null,
                    System.currentTimeMillis(),
                    null));

            runAsync(subscriber, input);
        };
    }

    /**
     * Executes the graph asynchronously (non-blocking, reactive stream) for the given run
     * request, translating each produced streaming/node output into AG-UI events emitted
     * to the given subscriber, and finally emitting completion or error events before
     * completing the subscriber.
     *
     * @param subscriber the subscriber to which AG-UI events are published
     * @param input      the run request describing the execution to perform
     */
    private void runAsync( Flow.Subscriber<? super Event> subscriber, RunAgentInput input) {

            graph.stream(graphInput(input), runnableConfig(input))
                    .forEachAsync(event -> {
                        if (event instanceof StreamingOutput<? extends AgentState> output) {
                            var messageId = streamingId.get();
                            if (messageId == null) {
                                log.trace("STREAMING START");
                                messageId = streamingId.updateAndGet(v -> newMessageId());
                                subscriber.onNext(new TextMessageStartEvent(
                                        messageId,
                                        Role.ASSISTANT,
                                        System.currentTimeMillis(),
                                        null));
                                return;
                            }
                            if (output.isStreamingEnd()) { // is streaming out ended
                                log.trace("STREAMING END");
                                streamingId.set(null);
                                subscriber.onNext(new TextMessageEndEvent(
                                        messageId,
                                        System.currentTimeMillis(),
                                        null));
                                return;
                            }

                            if (output.chunk() == null || output.chunk().isEmpty()) {
                                log.trace("STREAMING CHUNK IS EMPTY");
                            } else {
                                log.trace("{}", output.chunk());
                                subscriber.onNext(new TextMessageContentEvent(
                                        messageId,
                                        output.chunk(),
                                        System.currentTimeMillis(),
                                        null));
                                return;
                            }
                        }

                        log.trace("NEXT:\n{}", event);
                        onNextEvents(input, event).forEach(subscriber::onNext);

                    })
                    .thenApply(GraphResult::from)
                    .whenComplete( (result, error) -> {
                        if (error != null) {
                            try {
                                releaseOnError(input, error);
                                onErrorEvents(input, error).forEach(subscriber::onNext);
                            } catch (Exception ex) {
                                subscriber.onError(ex);
                            }
                        } else {
                            onCompleteEvents(input, result).forEach(subscriber::onNext);
                        }
                        subscriber.onComplete();
                    });

    };

    /**
     * Executes the graph synchronously (blocking iteration) for the given run request,
     * translating each produced streaming/node output into AG-UI events emitted to the
     * given subscriber, and finally emitting completion or error events before completing
     * the subscriber.
     *
     * @param subscriber the subscriber to which AG-UI events are published
     * @param input      the run request describing the execution to perform
     */
    private void runSync( Flow.Subscriber<? super Event> subscriber, RunAgentInput input) {
            try {
                final var generator = graph.stream(graphInput(input), runnableConfig(input));

                for (var event : generator) {
                    if (event instanceof StreamingOutput<? extends AgentState> output) {
                        var messageId = streamingId.get();
                        if (messageId == null) {
                            log.trace("STREAMING START");
                            messageId = streamingId.updateAndGet(v -> newMessageId());
                            subscriber.onNext( new TextMessageStartEvent(
                                    messageId,
                                    Role.ASSISTANT,
                                    System.currentTimeMillis(),
                                    null));
                            continue;
                        }
                        if (output.isStreamingEnd()) { // is streaming out ended
                            log.trace("STREAMING END");
                            streamingId.set(null);
                            subscriber.onNext( new TextMessageEndEvent(
                                    messageId,
                                    System.currentTimeMillis(),
                                    null));
                            continue;
                        }

                        if (output.chunk() == null || output.chunk().isEmpty()) {
                            log.trace("STREAMING CHUNK IS EMPTY");
                        } else {
                            log.trace("{}", output.chunk());
                            subscriber.onNext(new TextMessageContentEvent(
                                    messageId,
                                    output.chunk(),
                                    System.currentTimeMillis(),
                                    null));
                        }
                        continue;
                    }

                    log.trace("NEXT:\n{}", event);
                    onNextEvents(input, event).forEach(subscriber::onNext);

                }

                final var result = GraphResult.from(generator);

                onCompleteEvents(input, result).forEach(subscriber::onNext);
            } catch (Exception e) {
                try {
                    releaseOnError(input, e);
                    onErrorEvents(input, e).forEach(subscriber::onNext);
                } catch (Exception ex) {
                    subscriber.onError(ex);
                }
            }
            finally {
                subscriber.onComplete();
            }
    }



}
