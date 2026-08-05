package org.bsc.langgraph4j.langchain4j.serializer.std;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
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

public class SerializationTest {

    private static class State extends MessagesState<ChatMessage> {
        public State(Map<String, Object> initData) {
            super(initData);
        }
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
    public void UserMessageSingleTextSerializerTest() throws Exception {
        var userMessage = UserMessage.builder()
                .name("query")
                .addContent( new TextContent("query text") )
                .build();

        assertNotNull( userMessage );

        var serializer = new LC4jStateSerializer<>(State::new);

        var state = new State( Map.of(
                "messages", List.of(userMessage) )
        );

        try(var baos = new ByteArrayOutputStream(); var out = new ObjectOutputStream(baos) ) {

            serializer.write(state, out);

            try( var in = new ObjectInputStream( new ByteArrayInputStream(baos.toByteArray() )) ) {

                var newState = serializer.read( in );

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

        };



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

        var serializer = new LC4jStateSerializer<>(State::new);

        var state = new State( Map.of(
                "messages", List.of(userMessage) )
        );

        try(var baos = new ByteArrayOutputStream(); var out = new ObjectOutputStream(baos) ) {

            serializer.write(state, out);

            try( var in = new ObjectInputStream( new ByteArrayInputStream(baos.toByteArray() )) ) {

                var newState = serializer.read( in );

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

        };



    }

    @Test
    public void AiMessageTextAndToolExecutionRequestsSerializerTest() throws Exception {

        var aiMessage = AiMessage.builder()
                .text( "Some message for the user" )
                .toolExecutionRequests( List.of(
                        ToolExecutionRequest.builder()
                                .id( "1" )
                                .name( "someTool" )
                                .arguments( "{}" )
                                .build()
                ) )
                .build();

        var serializer = new LC4jStateSerializer<>(State::new);

        var state = new State( Map.of(
                "messages", List.of(aiMessage) )
        );

        try(var baos = new ByteArrayOutputStream(); var out = new ObjectOutputStream(baos) ) {

            serializer.write(state, out);

            try( var in = new ObjectInputStream( new ByteArrayInputStream(baos.toByteArray() )) ) {

                var newState = serializer.read( in );

                assertNotNull( newState );
                assertEquals( 1, newState.messages().size());
                var message = newState.messages().get( 0 );
                assertInstanceOf( AiMessage.class, message );

                var newAiMessage = (AiMessage)message;

                assertEquals( aiMessage.text(), newAiMessage.text() );
                assertEquals( aiMessage.toolExecutionRequests(), newAiMessage.toolExecutionRequests() );
            }

        };

    }

    @Test
    public void AiMessageOnlyToolExecutionRequestsSerializerTest() throws Exception {

        var aiMessage = AiMessage.builder()
                .toolExecutionRequests( List.of(
                        ToolExecutionRequest.builder()
                                .id( "1" )
                                .name( "someTool" )
                                .arguments( "{}" )
                                .build()
                ) )
                .build();

        var serializer = new LC4jStateSerializer<>(State::new);

        var state = new State( Map.of(
                "messages", List.of(aiMessage) )
        );

        try(var baos = new ByteArrayOutputStream(); var out = new ObjectOutputStream(baos) ) {

            serializer.write(state, out);

            try( var in = new ObjectInputStream( new ByteArrayInputStream(baos.toByteArray() )) ) {

                var newState = serializer.read( in );

                var newAiMessage = (AiMessage)newState.messages().get( 0 );

                assertNull( newAiMessage.text() );
                assertEquals( aiMessage.toolExecutionRequests(), newAiMessage.toolExecutionRequests() );
            }

        };

    }
}
