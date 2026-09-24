package org.bsc.langgraph4j.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.bsc.langgraph4j.GraphArgs;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.bsc.langgraph4j.utils.TryFunction;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.GraphInput;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public interface OTELObservable extends LG4JLoggable {

    default OpenTelemetry otel() {
        return Optional.ofNullable( io.opentelemetry.api.GlobalOpenTelemetry.get() )
                .orElseGet(OpenTelemetry::noop);
    }

    default <T, U, Ex extends Throwable> TryFunction<T, U, Ex> shareBaggageToCall(Baggage baggage, TryFunction<T, U, Ex> function ) throws Ex {

        final var otelContext = requireNonNull( baggage, "baggage cannot be null")
                .storeInContext( io.opentelemetry.context.Context.current() );
        return (t) -> {
            try( Scope ignored = otelContext.makeCurrent() ) {
                return function.apply(t);
            }
        };
    }

    static Baggage sharedBaggage() {
        return Baggage.fromContext(Context.current());
    }

    static Attributes attrsOf(Baggage baggage ) {
        if( baggage.isEmpty() ) {
            return Attributes.empty();
        }
        var attrsBuilder = Attributes.builder();

        baggage.forEach( (key,entry ) ->
                attrsBuilder.put( key, entry.getValue()));

        return attrsBuilder.build();
    }

    static Attributes attrsOf(Map<String,Object> data, StateSerializer<?> serializer )  {
        final var attrsBuilder = Attributes.builder();

        if( serializer instanceof PlainTextStateSerializer<?> textSerializer ) {
            try {
                attrsBuilder.put( "lg4j.state", textSerializer.writeDataAsString(data) );
            } catch (IOException e) {
                log.warn("OTEL state serialization error", e);
            }
        }
        else {
            attrsBuilder.put( "lg4j.state", CollectionsUtils.toString(data));
        }
        return attrsBuilder.build();
    }

    static Attributes attrsOf(InterruptionMetadata<?> interruptionMetaData, StateSerializer<?> serializer  ) {
        final var attrsBuilder = Attributes.builder();

        attrsBuilder.put( "lg4j.nodeId", interruptionMetaData.nodeId());

        interruptionMetaData.metadataKeys().forEach( key ->
                attrsBuilder.put( format("lg4j.metadata.%s", key),
                        interruptionMetaData.metadata(key).map(Object::toString).orElse(null)) );

        if( serializer instanceof PlainTextStateSerializer<?> textSerializer ) {
            try {
                attrsBuilder.put( "lg4j.state", textSerializer.writeDataAsString(interruptionMetaData.state().data()) );
            } catch (IOException e) {
                log.warn("OTEL state serialization error", e);
            }
        }
        else {
            attrsBuilder.put( "lg4j.state", CollectionsUtils.toString(interruptionMetaData.state().data()));
        }
        return attrsBuilder.build();
    }

    static Attributes attrsOf(Command command, StateSerializer<?> serializer  ) {
        final var attrsBuilder = Attributes.builder();

        attrsBuilder.put( "lg4j.command.gotoNode", command.gotoNodeSafe().orElse("null"));

        if (serializer instanceof PlainTextStateSerializer<?> textSerializer) {
            try {
                attrsBuilder.put("lg4j.command.update", textSerializer.writeDataAsString(command.update()));
            } catch (IOException e) {
                log.warn("OTEL state serialization error", e);
            }
        } else {
            attrsBuilder.put("lg4j.state", CollectionsUtils.toString(command.update()));
        }

        return attrsBuilder.build();
    }

    static Attributes attrsOf(RunnableConfig config)  {
        final var attrsBuilder = Attributes.builder()
                .put("lg4j.runnableConfig.isRunningStudio", config.isRunningInStudio())
                .put("lg4j.runnableConfig.threadId", config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT))
                .put("lg4j.runnableConfig.streamMode", config.streamMode().name());
        config.checkPointId().ifPresent(checkPointId -> attrsBuilder.put("lg4j.runnableConfig.checkPointId", checkPointId));

        return attrsBuilder.build();
    }

    static Attributes attrsOf(GraphInput input)  {
        if (input instanceof GraphArgs args) {
            return Attributes.of( stringKey("input.args"), CollectionsUtils.toString(args.value()));
        }
        return Attributes.of( booleanKey("input.resume"), true);
    }

    class TracerHolder {

        final String scope;
        final OpenTelemetry otel;

        public TracerHolder(OpenTelemetry otel, String scope) {
            this.scope = requireNonNull(scope, "scope cannot be null");
            this.otel = requireNonNull(otel, "otel cannot be null");
        }

        public Tracer object() {
            return otel.getTracer(scope);
        }

        public SpanBuilder spanBuilder(String spanName) {
            return object().spanBuilder(requireNonNull(spanName, "spanName cannot be null"));
        }

        public Optional<Span> currentSpan() {
            return ( Span.current().isRecording() ) ? Optional.of(Span.current()) : Optional.empty();
        }

        public <R> R tryApplySpan(Span span, TryFunction<Span,R,Exception> function) throws Exception {
            requireNonNull(span, "span cannot be null");
            requireNonNull(function, "function cannot be null");

            try {
                var result = function.apply(span);
                span.setStatus(StatusCode.OK);
                return result;
            } catch (Exception e) {
                if (span.isRecording()) {
                    span.recordException(e);
                }
                throw e;
            } finally {
                span.end();
            }

        }

        public <R> R applySpan(Span span, Function<Span, R> function) {
            requireNonNull(span, "span cannot be null");
            requireNonNull(function, "function cannot be null");
            try {
                var result = function.apply(span);
                span.setStatus(StatusCode.OK);
                return result;
            } finally {
                span.end();
            }

        }

    }

    class MeterHolder {
        final String scope;
        final OpenTelemetry otel;

        public MeterHolder( OpenTelemetry otel, String scope ) {
            this.scope = requireNonNull( scope, "scope cannot be null");
            this.otel = requireNonNull(otel, "otel cannot be null");
        }

        public Meter object() {
            return otel.getMeter(scope);
        }

        public LongCounterBuilder countBuilder(String counterName ) {
            return object().counterBuilder( requireNonNull(counterName, "counterName cannot be null"));
        }

        public DoubleGaugeBuilder gaugeBuilder(String gaugeName ) {
            return object().gaugeBuilder( requireNonNull(gaugeName, "gaugeName cannot be null"));
        }

        public DoubleHistogramBuilder histogramBuilder(String histogramName ) {
            return object().histogramBuilder( requireNonNull(histogramName, "histogramName cannot be null"));
        }

        public LongUpDownCounterBuilder upDownCounterBuilder(String upDownCounterName ) {
            return object().upDownCounterBuilder( requireNonNull(upDownCounterName, "upDownCounterName cannot be null"));
        }

    }

}
