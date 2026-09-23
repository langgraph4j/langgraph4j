package com.agui.json;

import com.agui.community.core.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("AGUI JacksonSerializer Message Test")
class AGUIJacksonSerializerMessageTest {

    private AGUIJacksonSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new AGUIJacksonSerializer();
    }

    @Test
    public void testAssistantMessageSerialization() {

        Message messageIn = new AssistantMessage( "1", "Hello, World!");

        // Test serialization
        var json = serializer.serialize(messageIn);
        assertEquals("""
                {"role":"assistant","id":"1","content":"Hello, World!","toolCalls":[]}""",
                json);

        // Test deserialization
        AssistantMessage messageOut = serializer.deserialize(json, AssistantMessage.class);
        assertEquals("Hello, World!", messageOut.content());


        var tools = List.of( new ToolCall("tool1", new FunctionCall("tool_1", "{}")));

        messageIn = new AssistantMessage( "1", "Hello, World!", "Assistant", tools);

        // Test serialization
        json = serializer.serialize(messageIn);
        assertEquals("""
                {"role":"assistant","id":"1","content":"Hello, World!","name":"Assistant","toolCalls":[{"id":"tool1","function":{"name":"tool_1","arguments":"{}"}}]}""",
                json);

        // Test deserialization
        messageOut = serializer.deserialize(json, AssistantMessage.class);
        assertEquals("Hello, World!", messageOut.content());
        assertEquals(1, messageOut.toolCalls().size());
        var toolCall = messageOut.toolCalls().get(0);
        assertEquals("tool1", toolCall.id());
        assertEquals("tool_1", toolCall.function().name());
        assertEquals("{}", toolCall.function().arguments());

    }

    @Test
    public void testSystemMessageSerialization() {
        Message messageIn = new SystemMessage( "1", "Hello, World!");

        // Test serialization
        var json = serializer.serialize(messageIn);
        assertEquals("""
                {"role":"system","id":"1","content":"Hello, World!"}""",
                json);

        // Test deserialization
        SystemMessage messageOut = serializer.deserialize(json, SystemMessage.class);
        assertEquals("Hello, World!", messageOut.content());


    }

    @Test
    public void testUserMessageSerialization() {
        Message messageIn = new UserMessage("1", "Hello, World!");

        // Test serialization
        var json = serializer.serialize(messageIn);
        assertEquals("""
                        {"role":"user","id":"1","content":"Hello, World!"}""",
                json);

        // Test deserialization
        UserMessage messageOut = serializer.deserialize(json, UserMessage.class);
        assertEquals("Hello, World!", messageOut.content());
    }

    @Test
    public void testDeveloperMessage() {
        Message messageIn = new DeveloperMessage("1", "Hello, World!");

        // Test serialization
        var json = serializer.serialize(messageIn);
        assertEquals("""
                        {"role":"developer","id":"1","content":"Hello, World!"}""",
                json);

        // Test deserialization
        DeveloperMessage messageOut = serializer.deserialize(json, DeveloperMessage.class);
        assertEquals("Hello, World!", messageOut.content());
    }

    @Test
    public void testToolMessageSerialization() {
        Message messageIn = new ToolMessage("1", "Hello, World!", "tool_call_1", null);

        // Test serialization
        var json = serializer.serialize(messageIn);
        assertEquals("""
                        {"role":"tool","id":"1","content":"Hello, World!","toolCallId":"tool_call_1"}""",
                json);

        // Test deserialization
        ToolMessage messageOut = serializer.deserialize(json, ToolMessage.class);
        assertEquals("Hello, World!", messageOut.content());
        assertEquals("tool_call_1", messageOut.toolCallId());
        assertNull(messageOut.error());
    }
}