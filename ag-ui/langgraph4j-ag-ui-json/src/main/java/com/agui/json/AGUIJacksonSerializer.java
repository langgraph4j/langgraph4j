package com.agui.json;

import com.agui.community.core.event.Event;
import com.agui.community.core.interrupt.OutcomeType;
import com.agui.community.core.interrupt.Resume;
import com.agui.community.core.interrupt.ResumeStatus;
import com.agui.community.core.interrupt.RunOutcome;
import com.agui.community.core.message.Message;
import com.agui.community.core.message.Role;
import com.agui.community.core.serialization.Serializer;
import com.agui.json.mixins.EventMixin;
import com.agui.json.mixins.MessageMixin;
import com.agui.json.mixins.EnumWithValueMixin;
import com.agui.json.mixins.OutcomeMixin;
import com.agui.json.mixins.ResumeMixin;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;

/**
 * Jackson-based implementation of the AG-UI {@link Serializer} protocol.
 * <p>
 * Configures a Jackson {@link ObjectMapper} suited to (de)serialize AG-UI core model classes
 * (events, messages, interrupt/resume outcomes, enums with a JSON value, ...) to/from JSON,
 * registering the required mixins ({@link EventMixin}, {@link MessageMixin}, {@link OutcomeMixin},
 * {@link ResumeMixin}, {@link EnumWithValueMixin}) only if not already present, and relaxing
 * deserialization so unknown properties are ignored and only non-null properties are serialized.
 */
public class AGUIJacksonSerializer implements Serializer {

    private final ObjectMapper objectMapper;

    /**
     * Creates a new serializer, building and configuring the underlying Jackson
     * {@link ObjectMapper} with the mixins required to handle AG-UI core model classes.
     */
    public AGUIJacksonSerializer() {

        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .build();

        objectMapper = JsonMapper.builder(factory)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultPropertyInclusion(JsonInclude.Value.ALL_NON_NULL)
                .build();


        if (Objects.isNull(objectMapper.findMixInClassFor(Role.class))) {
            objectMapper.addMixIn(Role.class, EnumWithValueMixin.class);
        }
        if (Objects.isNull(objectMapper.findMixInClassFor(OutcomeType.class))) {
            objectMapper.addMixIn(OutcomeType.class, EnumWithValueMixin.class);
        }
        if (Objects.isNull(objectMapper.findMixInClassFor(ResumeStatus.class))) {
            objectMapper.addMixIn(ResumeStatus.class, EnumWithValueMixin.class);
        }

        // ADD MIXINS FOR AGUI CORE CLASSES IF NOT ALREADY PRESENT
        if (Objects.isNull(objectMapper.findMixInClassFor(Message.class))) {
            objectMapper.addMixIn(Message.class, MessageMixin.class);
        }
        if (Objects.isNull(objectMapper.findMixInClassFor(Event.class))) {
            objectMapper.addMixIn(Event.class, EventMixin.class);
        }
        if (Objects.isNull(objectMapper.findMixInClassFor(RunOutcome.class))) {
            objectMapper.addMixIn(RunOutcome.class, OutcomeMixin.class);
        }
        if (Objects.isNull(objectMapper.findMixInClassFor(Resume.class))) {
            objectMapper.addMixIn(Resume.class, ResumeMixin.class);
        }
    }

    /**
     * Serializes the given value to its JSON string representation.
     *
     * @param value the object to serialize
     * @return the JSON representation of {@code value}
     * @throws RuntimeException if serialization fails
     */
    @Override
    public String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Deserializes the given JSON string into an instance of the given type.
     *
     * @param json the JSON string to deserialize
     * @param type the target type
     * @param <T>  the target type
     * @return the deserialized instance
     * @throws RuntimeException if deserialization fails
     */
    @Override
    public <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to object", e);
        }
    }

    /**
     * Deserializes the given JSON string into a list of elements of the given type.
     *
     * @param json        the JSON string to deserialize
     * @param elementType the type of the list elements
     * @param <T>         the type of the list elements
     * @return the deserialized list
     * @throws RuntimeException if deserialization fails
     */
    @Override
    public <T> List<T> deserializeList(String json, Class<T> elementType) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to list", e);
        }
    }
}
