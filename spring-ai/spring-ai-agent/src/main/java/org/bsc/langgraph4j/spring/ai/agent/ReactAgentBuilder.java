package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.messages.Message;

public abstract class ReactAgentBuilder<B extends ReactAgentBuilder<B,State>, State extends MessagesState<Message>> extends BaseReactAgentBuilder<B,State> {
    protected Agent.Builder<Message, AgentExecutor.State> agentBuilder;

    public ReactAgentBuilder() {
        this.agentBuilder = Agent.builder();;
    }

    public ReactAgentBuilder(ReactAgentBuilder<?, State> builder) {
        super(builder);
        this.agentBuilder = builder.agentBuilder;
    }

    public B addCallModelHook(NodeHook.WrapCall<AgentExecutor.State> wrapCall ) {
        agentBuilder.addCallModelHook(wrapCall);
        return result();
    }

    public B addExecuteToolsHook(EdgeHook.WrapCall<AgentExecutor.State> wrapCall ) {
        agentBuilder.addExecuteToolsHook(wrapCall);
        return result();
    }

}
