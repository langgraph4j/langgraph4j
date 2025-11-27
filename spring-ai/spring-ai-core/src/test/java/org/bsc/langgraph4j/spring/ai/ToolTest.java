package org.bsc.langgraph4j.spring.ai;

import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.spring.ai.tool.SpringAIToolResponseBuilder;
import org.bsc.langgraph4j.spring.ai.tool.SpringAIToolService;
import org.bsc.langgraph4j.utils.TypeRef;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

public class ToolTest {

    public static class Tools {

        @Tool(description = "tool for test AI agent executor")
        String execTest(@ToolParam(description = "test message") String message) {
            return format("test tool ('%s') executed", message);
        }

        @Tool(description = "tool for test AI agent executor")
        String execTest2(@ToolParam(description = "test message") String message, ToolContext context ) {

            return SpringAIToolResponseBuilder.of(context)
                    .update( Map.of( "arg0", message, "arg1", "execTest2" ) )
                    .gotoNode( "END" )
                    .buildAndReturn( format("test tool ('%s') executed", message) );

        }

        @Tool( description="return current number of system thread allocated by application")
        int threadCount( ToolContext context ) {

            var stackTrace =  Thread.getAllStackTraces();

            return SpringAIToolResponseBuilder.of(context)
                    .update( Map.of( "stackTrace", stackTrace ) )
                    .buildAndReturn( stackTrace.size() );
        }

    }

    @Test
    public void testCallTool() {
        var tools = ToolCallbacks.from( new Tools() );

        assertNotNull( tools );

        var toolService = new SpringAIToolService( List.of(tools) );

        var toolName = "execTest";
        var optionalTool = toolService.agentFunction(toolName);

        assertTrue(optionalTool.isPresent());

        var toolCall = new AssistantMessage.ToolCall(
                "call_1",
                "function",
                toolName,
                "{ \"arg0\": \"test1\"}");

        Command callResult = toolService.executeFunctions(List.of(toolCall), Map.of()).join();

        assertNotNull(callResult);
        assertTrue(callResult.gotoNodeSafe().isEmpty());
        assertNotNull(callResult.update());
        assertEquals(1, callResult.update().size());

        assertNotNull(callResult.update().get("messages"));

        var typeRef = new TypeRef<ToolResponseMessage>() {
        };
        var message = typeRef.cast(callResult.update().get("messages"));

        assertTrue(message.isPresent());
        assertEquals(1, message.get().getResponses().size());
        assertEquals("call_1", message.get().getResponses().get(0).id());
        assertEquals("\"test tool ('test1') executed\"", message.get().getResponses().get(0).responseData());

    }

    @Test
    public void testCallToolThatUpdateState() {
        var tools = ToolCallbacks.from( new Tools() );

        assertNotNull( tools );

        var toolService = new SpringAIToolService( List.of(tools) );

        var toolName = "threadCount";

        var optionalTool = toolService.agentFunction(toolName);

        assertTrue(optionalTool.isPresent());

        var toolCall = new AssistantMessage.ToolCall(
                "call_1",
                "function",
                toolName,
                "{}");

        Command callResult = toolService.executeFunctions(List.of(toolCall), Map.of()).join();

        assertNotNull(callResult);
        assertTrue(callResult.gotoNodeSafe().isEmpty());
        assertNotNull(callResult.update());
        assertEquals(2, callResult.update().size());


        assertNotNull(callResult.update().get("stackTrace"));

        var stackTraceTypeRef = new TypeRef<Map<Thread, StackTraceElement[]>>() {};
        var stackTrace = stackTraceTypeRef.cast(callResult.update().get("stackTrace"));
        assertTrue(stackTrace.isPresent());

        assertNotNull(callResult.update().get("messages"));

        var typeRef = new TypeRef<ToolResponseMessage>() {};
        var message = typeRef.cast(callResult.update().get("messages"));

        assertTrue(message.isPresent());
        assertEquals(1, message.get().getResponses().size());
        assertEquals("call_1", message.get().getResponses().get(0).id());
        assertEquals( Objects.toString(stackTrace.get().size()),
                message.get().getResponses().get(0).responseData());

    }

    @Test
    public void testCallToolThatUpdateStateAndSetGotoNode() {
        var tools = ToolCallbacks.from( new Tools() );

        assertNotNull( tools );

        var toolService = new SpringAIToolService( List.of(tools) );

        var toolName = "execTest2";

        var optionalTool = toolService.agentFunction(toolName);

        assertTrue(optionalTool.isPresent());

        var toolCall = new AssistantMessage.ToolCall(
                "call_1",
                "function",
                toolName,
                "{ \"arg0\": \"test2\"}");

        Command callResult = toolService.executeFunctions(List.of(toolCall), Map.of()).join();

        assertNotNull(callResult);

        assertTrue(callResult.gotoNodeSafe().isPresent());
        assertEquals("END", callResult.gotoNodeSafe().get());

        assertNotNull(callResult.update());
        assertEquals(3, callResult.update().size());
        assertNotNull(callResult.update().get("arg0"));
        assertEquals("test2", callResult.update().get("arg0"));
        assertNotNull(callResult.update().get("arg1"));
        assertEquals(toolName, callResult.update().get("arg1"));

        assertNotNull(callResult.update().get("messages"));

        var typeRef = new TypeRef<ToolResponseMessage>() {};
        var message = typeRef.cast(callResult.update().get("messages"));

        assertTrue(message.isPresent());
        assertEquals(1, message.get().getResponses().size());
        assertEquals("call_1", message.get().getResponses().get(0).id());
        assertEquals("\"test tool ('test2') executed\"", message.get().getResponses().get(0).responseData());

    }

}