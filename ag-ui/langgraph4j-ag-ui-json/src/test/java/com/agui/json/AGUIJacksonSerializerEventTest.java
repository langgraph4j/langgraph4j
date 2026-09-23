package com.agui.json;

import com.agui.community.core.event.Event;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.message.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AGUIJacksonSerializerEventTest {

    private AGUIJacksonSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new AGUIJacksonSerializer();
    }

    @Test
    public void testTextMessageStartEventSerialization() {
        Event eventIn = new TextMessageStartEvent( "1", Role.ASSISTANT);

        // Test serialization
        var json = serializer.serialize(eventIn);
        assertEquals("""
                {"type":"TEXT_MESSAGE_START","messageId":"1","role":"assistant"}""", json);

        // Test deserialization
        final TextMessageStartEvent eventOut = serializer.deserialize(json, TextMessageStartEvent.class);
        assertEquals("1", eventOut.messageId());
        assertEquals(Role.ASSISTANT, eventOut.role());

    }

    @Test
    public void testTextMessageEndEventSerialization() {
        final var timestamp = System.currentTimeMillis();

        // Implement test for TextMessageEndEvent serialization and deserialization
        Event eventIn = new TextMessageEndEvent( "1", timestamp, null);

        // Test serialization
        var json = serializer.serialize(eventIn);
        assertEquals("""
                {"type":"TEXT_MESSAGE_END","messageId":"1","timestamp":%d}"""
                .formatted(timestamp), json);

        // Test deserialization
        final TextMessageEndEvent eventOut = serializer.deserialize(json, TextMessageEndEvent.class);
        assertEquals("1", eventOut.messageId());
        assertEquals(timestamp, eventOut.timestamp());

    }
}
