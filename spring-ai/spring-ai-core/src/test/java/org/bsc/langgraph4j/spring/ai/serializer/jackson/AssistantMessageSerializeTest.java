package org.bsc.langgraph4j.spring.ai.serializer.jackson;

import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AssistantMessageSerializeTest {

    @Test
    public void textOnlyTest() throws Exception {

        var serializer = new SpringAIJacksonStateSerializer<>( AgentState::new );

        var message = new AssistantMessage( "Hello world");
        var state = serializer.cloneObject( Map.of( "assistant1", message) );

        assertEquals(1, state.data().size());
        assertTrue( state.value( "assistant1").isPresent() );
        var stateValue = state.<AssistantMessage>value( "assistant1").orElseThrow();
        assertEquals( "Hello world", stateValue.getText() );
        assertFalse( stateValue.getMetadata().isEmpty() );
        assertEquals( 1, stateValue.getMetadata().size() );
        assertTrue( stateValue.getMetadata().containsKey("messageType"));
        assertEquals(MessageType.ASSISTANT, stateValue.getMetadata().get("messageType") );
        assertTrue( stateValue.getToolCalls().isEmpty() );


    }

    @Test
    public void textAndMetadataTest() throws Exception {

        var serializer = new SpringAIJacksonStateSerializer<>( AgentState::new );


        Map<String,Object> metadata = Map.of( "key1", "value1", "key2", 100 );

        var message = AssistantMessage.builder()
            .content("Hello world")
            .properties(metadata)
            .build();

        var state = serializer.cloneObject( Map.of( "assistant1", message) );

        assertEquals(1, state.data().size());
        assertTrue( state.value( "assistant1").isPresent() );
        assertInstanceOf( AssistantMessage.class, state.value( "assistant1").get() );

        var stateValue = state.<AssistantMessage>value( "assistant1").orElseThrow();
        assertEquals( "Hello world", stateValue.getText() );
        assertFalse( stateValue.getMetadata().isEmpty() );
        assertEquals( 3, stateValue.getMetadata().size() );
        assertTrue( stateValue.getMetadata().containsKey("messageType"));
        assertEquals(MessageType.ASSISTANT, stateValue.getMetadata().get("messageType") );
        assertTrue( stateValue.getMetadata().containsKey("key1"));
        assertEquals("value1", stateValue.getMetadata().get("key1") );
        assertTrue( stateValue.getMetadata().containsKey("key2"));
        assertEquals(100, stateValue.getMetadata().get("key2") );
        assertTrue( stateValue.getToolCalls().isEmpty() );

    }


    @Test
    public void fullTest() throws Exception {

        var serializer = new SpringAIJacksonStateSerializer<>( AgentState::new );

        Map<String,Object> metadata = Map.of( "key1", "value1", "key2", 100 );

        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall( "t1", "function", "test1", "{}" ),
                new AssistantMessage.ToolCall( "t2", "function", "test2", "{ param: 'value'}" )
        );

        var message = AssistantMessage.builder()
            .content("Hello world")
            .properties(metadata)
            .toolCalls(toolCalls)
            .build();

        var state = serializer.cloneObject( Map.of( "assistant1", message) );

        assertEquals(1, state.data().size());
        assertTrue( state.value( "assistant1").isPresent() );
        assertInstanceOf( AssistantMessage.class, state.value( "assistant1").get() );

        var stateValue = state.<AssistantMessage>value( "assistant1").orElseThrow();
        assertEquals( "Hello world", stateValue.getText() );
        assertFalse( stateValue.getMetadata().isEmpty() );
        assertEquals( 3, stateValue.getMetadata().size() );
        assertTrue( stateValue.getMetadata().containsKey("messageType"));
        assertEquals(MessageType.ASSISTANT, stateValue.getMetadata().get("messageType") );
        assertTrue( stateValue.getMetadata().containsKey("key1"));
        assertEquals("value1", stateValue.getMetadata().get("key1") );
        assertTrue( stateValue.getMetadata().containsKey("key2"));
        assertEquals(100, stateValue.getMetadata().get("key2") );
        assertFalse( stateValue.getToolCalls().isEmpty() );
        assertEquals( 2, stateValue.getToolCalls().size() );
        assertEquals( toolCalls, stateValue.getToolCalls() );
    }

}
