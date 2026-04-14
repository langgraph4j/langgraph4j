package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.TypeMapper;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

public class LC4jJacksonStateSerializer <State extends AgentState>  extends JacksonStateSerializer<State> {

    interface ChatMessageDeserializer {
        SystemMessageHandler.Deserializer system = new SystemMessageHandler.Deserializer();
        UserMessageHandler.Deserializer user = new UserMessageHandler.Deserializer();
        AiMessageHandler.Deserializer ai = new AiMessageHandler.Deserializer();
        ToolExecutionResultMessageHandler.Deserializer tool = new ToolExecutionResultMessageHandler.Deserializer();

        static void registerTo( SimpleModule module ) {
            module
                    .addDeserializer(ToolExecutionResultMessage.class, tool)
                    .addDeserializer(SystemMessage.class, system )
                    .addDeserializer(UserMessage.class, user )
                    .addDeserializer(AiMessage.class, ai )
            ;
        }

    }

    interface ChatMessageSerializer  {
        SystemMessageHandler.Serializer system = new SystemMessageHandler.Serializer();
        UserMessageHandler.Serializer user = new UserMessageHandler.Serializer();
        AiMessageHandler.Serializer ai = new AiMessageHandler.Serializer();
        ToolExecutionResultMessageHandler.Serializer tool = new ToolExecutionResultMessageHandler.Serializer();

        static void registerTo( SimpleModule module ) {
            module
                    .addSerializer(ToolExecutionResultMessage.class, tool)
                    .addSerializer(SystemMessage.class, system)
                    .addSerializer(UserMessage.class, user)
                    .addSerializer(AiMessage.class, ai)
            ;

        }

    }

    public LC4jJacksonStateSerializer(AgentStateFactory<State> stateFactory) {
        super(stateFactory);

        var module = new SimpleModule();

        LC4jJacksonStateSerializer.ChatMessageSerializer.registerTo(module);
        LC4jJacksonStateSerializer.ChatMessageDeserializer.registerTo(module);

        typeMapper
                .register(new TypeMapper.Reference<ToolExecutionResultMessage>(ChatMessageType.TOOL_EXECUTION_RESULT.name()) {} )
                .register(new TypeMapper.Reference<SystemMessage>(ChatMessageType.SYSTEM.name()) {} )
                .register(new TypeMapper.Reference<UserMessage>(ChatMessageType.USER.name()) {} )
                .register(new TypeMapper.Reference<AiMessage>(ChatMessageType.AI.name()) {} )
                .register(new TypeMapper.Reference<Content>(ContentType.IMAGE.name()) {} )
                .register(new TypeMapper.Reference<ToolExecutionRequest>(ToolExecutionRequest.class.getName()) {} )
        ;

        module.addDeserializer( ToolExecutionRequest.class, new ToolExecutionRequestHandler.Deserializer() );
        module.addSerializer( ToolExecutionRequest.class, new ToolExecutionRequestHandler.Serializer() );

        module.addSerializer( Content.class, new ContentHandler.Serializer() );
        module.addDeserializer( Content.class, new ContentHandler.Deserializer() );

        objectMapper.registerModule( module );
        //objectMapper.setDefaultSetterInfo(JsonSetter.Value.forContentNulls(Nulls.SKIP));
    }
}
