package org.bsc.langgraph4j.spring.ai.serializer.jackson;

import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolResponseMessageTest {

    @Test
    public void withEmptyResponsesTest() throws Exception {

        var serializer = new SpringAIJacksonStateSerializer<>( AgentState::new );

        Map<String,Object> metadata = Map.of( "key1", "value1", "key2", 100 );

        List<ToolResponseMessage.ToolResponse> toolResponses = List.of(
        );

        var message = ToolResponseMessage.builder().responses(toolResponses).metadata(metadata).build();

        var state = serializer.cloneObject( Map.of( "tool_response", message) );

        assertEquals(1, state.data().size());
        assertTrue( state.value( "tool_response").isPresent() );
        assertInstanceOf( ToolResponseMessage.class, state.value( "tool_response").get() );

        var stateValue = state.<ToolResponseMessage>value( "tool_response").orElseThrow();
        assertTrue( ofNullable(stateValue.getText()).map( String::isBlank ).orElse( false ) );
        assertFalse( stateValue.getMetadata().isEmpty() );
        assertEquals( 3, stateValue.getMetadata().size() );
        assertTrue( stateValue.getMetadata().containsKey("messageType"));
        assertEquals(MessageType.TOOL, stateValue.getMetadata().get("messageType") );
        assertTrue( stateValue.getMetadata().containsKey("key1"));
        assertEquals("value1", stateValue.getMetadata().get("key1") );
        assertTrue( stateValue.getMetadata().containsKey("key2"));
        assertEquals(100, stateValue.getMetadata().get("key2") );
        assertTrue( stateValue.getResponses().isEmpty() );
    }

    @Test
    public void fullTestTest() throws Exception {

        var serializer = new SpringAIJacksonStateSerializer<>( AgentState::new );

        Map<String,Object> metadata = Map.of( "key1", "value1", "key2", 100 );

        List<ToolResponseMessage.ToolResponse> toolResponses = List.of(
                new ToolResponseMessage.ToolResponse( "t1", "test1", "{}" ),
                new ToolResponseMessage.ToolResponse( "t2", "test2", "{ result: 'OK'}" )
        );

        var message = ToolResponseMessage.builder().responses(toolResponses).metadata(metadata).build();

        var state = serializer.cloneObject( Map.of( "tool_response", message) );

        assertEquals(1, state.data().size());
        assertTrue( state.value( "tool_response").isPresent() );
        assertInstanceOf( ToolResponseMessage.class, state.value( "tool_response").get() );

        var stateValue = state.<ToolResponseMessage>value( "tool_response").orElseThrow();
        assertTrue( ofNullable(stateValue.getText()).map( String::isBlank ).orElse( false ) );
        assertFalse( stateValue.getMetadata().isEmpty() );
        assertEquals( 3, stateValue.getMetadata().size() );
        assertTrue( stateValue.getMetadata().containsKey("messageType"));
        assertEquals(MessageType.TOOL, stateValue.getMetadata().get("messageType") );
        assertTrue( stateValue.getMetadata().containsKey("key1"));
        assertEquals("value1", stateValue.getMetadata().get("key1") );
        assertTrue( stateValue.getMetadata().containsKey("key2"));
        assertEquals(100, stateValue.getMetadata().get("key2") );
        assertFalse( stateValue.getResponses().isEmpty() );
        assertEquals( 2, stateValue.getResponses().size() );
        assertEquals( toolResponses, stateValue.getResponses() );
    }

}
