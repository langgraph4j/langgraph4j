package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class JacksonSerializationTest {

    private static class State extends MessagesState<ChatMessage> {
        public State(Map<String, Object> initData) {
            super(initData);
        }
    }

    @Test
    public void StateSerializerTest() throws Exception {

        var serializer = new LC4jJacksonStateSerializer<>(State::new);

        var state = new State(Map.of("system", SystemMessage.from("Buddy"),
                "user", UserMessage.from("Hello")));

        var data = serializer.writeDataAsString(state);

        var result = serializer.readDataFromString(data);

        assertNotNull(result);
        assertEquals(2, result.data().size());
        var message = result.data().get("system");
        assertInstanceOf(SystemMessage.class, message);
        assertEquals("Buddy", ((SystemMessage) message).text());
        message = result.data().get("user");
        assertInstanceOf(UserMessage.class, message);
        assertEquals("Hello", ((UserMessage) message).singleText());


    }

    @Test
    public void MessagesStateSerializerTest() throws Exception {

        var serializer = new LC4jJacksonStateSerializer<>(State::new);

        var state = new State(Map.of(
                "messages", List.of(SystemMessage.from("Buddy"), UserMessage.from("Hello")),
                "intent", "myIntent")
        );

        var data = serializer.writeDataAsString(state);

        var result = serializer.readDataFromString(data);

        assertNotNull(result);
        assertEquals(2, result.data().size());
        var messages = result.data().get("messages");
        assertInstanceOf(List.class, messages);
        var messagesList = (List<?>) messages;
        assertEquals(2, messagesList.size());
        assertInstanceOf(SystemMessage.class, messagesList.get(0));
        assertEquals("Buddy", ((SystemMessage) messagesList.get(0)).text());

    }

    @Test
    public void AiMessageSerializerTest01() throws Exception {

        var serializer = new LC4jJacksonStateSerializer<>(State::new);

        var toolRequest1 = ToolExecutionRequest.builder()
                .id("id1")
                .name("name1")
                .arguments("arguments1")
                .build();
        var toolRequest2 = ToolExecutionRequest.builder()
                .name("name2")
                .arguments("arguments2")
                .build();
        var aiMessage = new AiMessage.Builder()
                .text("")
                .toolExecutionRequests(List.of(toolRequest1, toolRequest2))
                .build();

        var state = new State(Map.of(
                "messages", List.of(aiMessage))
        );

        var data = serializer.writeDataAsString(state);

        var result = serializer.readDataFromString(data);

        assertNotNull(result);
        assertTrue(result.lastMessage().isPresent());
        assertInstanceOf(AiMessage.class, result.lastMessage().get());

        var lastMessage = result.lastMessage().map(AiMessage.class::cast).orElseThrow();

        assertEquals("", lastMessage.text());
        assertTrue(lastMessage.hasToolExecutionRequests());
        assertEquals(2, lastMessage.toolExecutionRequests().size());
        var request = lastMessage.toolExecutionRequests().get(0);

        assertInstanceOf(ToolExecutionRequest.class, request);
        assertEquals("id1", request.id());
        assertEquals("name1", request.name());
        assertEquals("arguments1", request.arguments());

        request = lastMessage.toolExecutionRequests().get(1);

        assertInstanceOf(ToolExecutionRequest.class, request);
        assertNull(request.id());
        assertEquals("name2", request.name());
        assertEquals("arguments2", request.arguments());

    }

    @Test
    public void AiMessageSerializerTest02() throws Exception {

        var serializer = new LC4jJacksonStateSerializer<>(State::new);

        var aiMessage = new AiMessage.Builder()
                .text("My text")
                .toolExecutionRequests(List.of())
                .build();

        var state = new State(Map.of(
                "messages", List.of(aiMessage))
        );

        var data = serializer.writeDataAsString(state);

        var result = serializer.readDataFromString(data);

        assertNotNull(result);
        assertTrue(result.lastMessage().isPresent());
        assertInstanceOf(AiMessage.class, result.lastMessage().get());

        var lastMessage = result.lastMessage().map(AiMessage.class::cast).orElseThrow();

        assertEquals("My text", lastMessage.text());
        assertFalse(lastMessage.hasToolExecutionRequests());
    }

    public CompletableFuture<ImageContent> loadImageContentResource(String imagePath, String mimeType) {
        try (var inputStream = getClass().getResourceAsStream(imagePath)) {
            if (inputStream == null) {
                return failedFuture(new IOException("image not found: %s".formatted(imagePath)));
            }

            byte[] imageBytes = inputStream.readAllBytes();
            var base64String = Base64.getEncoder().encodeToString(imageBytes);

            final var img = Image.builder()
                    .base64Data( base64String )
                    //.url( imagePath )
                    .mimeType(mimeType)
                    .build();
            return completedFuture(ImageContent.from(img, ImageContent.DetailLevel.AUTO));

        } catch (Exception ex) {
            return failedFuture(new IOException("error loading image %s: %s".formatted(imagePath, ex.getMessage())));
        }
    }

    @Test
    public void ImageContentSerializerTest() throws Exception {

        final var imageContent = loadImageContentResource("/ReAct_image.png", "image/png").get();

        var serializer = new LC4jJacksonStateSerializer<>(AgentState::new);

        var stateData = Map.<String,Object>of("image", imageContent );

        var jsonString = serializer.writeDataAsString( stateData );

        assertNotNull( jsonString );

        var newStateData = serializer.readDataFromString( jsonString ).data();

        assertNotNull( newStateData );
        assertFalse( newStateData.isEmpty() );
        assertInstanceOf( ImageContent.class, newStateData.get("image"));

        var newImageContent = (ImageContent)newStateData.get("image");

        assertEquals( imageContent.image().url(), newImageContent.image().url() );
        assertEquals( imageContent.image().mimeType(), newImageContent.image().mimeType() );
        assertEquals( imageContent.image().base64Data(), newImageContent.image().base64Data() );
        assertEquals( imageContent.detailLevel(), newImageContent.detailLevel() );

    }


    @Test
    public void UserMessageSingleTextSerializerTest() throws Exception {
        var userMessage = UserMessage.builder()
                .name("query")
                .addContent( new TextContent("query text") )
                .build();

        var serializer = new LC4jJacksonStateSerializer<>(State::new);

        var state = new State( Map.of(
                "messages", List.of(userMessage) )
        );

        var jsonString = serializer.writeDataAsString( state.data() );

        assertNotNull( jsonString );

        var newState = serializer.readDataFromString( jsonString );

        assertNotNull( newState );
        assertFalse( newState.messages().isEmpty() );
        assertEquals( 1, newState.messages().size());
        var message = newState.messages().get( 0 );
        assertNotNull( message );
        assertInstanceOf( UserMessage.class, message );

        var newUserMessage = (UserMessage)message;

        assertTrue( newUserMessage.hasSingleText() );
        assertEquals( "query text", newUserMessage.singleText() );

    }

    @Test
    public void UserMessageImageSerializerTest() throws Exception {

        final var imageContent = loadImageContentResource("/ReAct_image.png", "image/png").get();

        var userMessage = UserMessage.builder()
                .name("query")
                .addContent( new TextContent("query text") )
                .addContent( imageContent )
                .build();

        assertNotNull( userMessage );

        var serializer = new LC4jJacksonStateSerializer<>(State::new);

        var state = new State( Map.of(
                "messages", List.of(userMessage) )
        );

        var jsonString = serializer.writeDataAsString( state.data() );

        assertNotNull( jsonString );

        var newState = serializer.readDataFromString( jsonString );

        assertNotNull( newState );
        assertFalse( newState.messages().isEmpty() );
        assertEquals( 1, newState.messages().size());
        var message = newState.messages().get( 0 );
        assertNotNull( message );
        assertInstanceOf( UserMessage.class, message );

        var newUserMessage = (UserMessage)message;

        assertEquals( 2, newUserMessage.contents().size() );
        List<Content> contents = newUserMessage.contents();
        var content1 = newUserMessage.contents().get(0);
        assertInstanceOf( TextContent.class, content1 );
        var newContent = newUserMessage.contents().get(1);
        assertInstanceOf( ImageContent.class, newContent );
        assertEquals( "query text", ((TextContent)content1).text() );
        var newImageContent = (ImageContent)newContent;
        assertEquals( imageContent.image().url(), newImageContent.image().url() );
        assertEquals( imageContent.image().mimeType(), newImageContent.image().mimeType() );
        assertEquals( imageContent.image().base64Data(), newImageContent.image().base64Data() );
        assertEquals( imageContent.detailLevel(), newImageContent.detailLevel() );

    }
}