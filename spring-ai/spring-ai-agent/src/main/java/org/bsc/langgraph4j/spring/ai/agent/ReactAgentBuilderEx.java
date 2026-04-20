package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class ReactAgentBuilderEx<B extends ReactAgentBuilderEx<B,State>, State extends MessagesState<Message>> extends BaseReactAgentBuilder<B,State> {

    protected final Map<String, AgentEx.ApprovalNodeAction<Message, State>> approvals;
    protected AgentEx.Builder<Message, AgentExecutorEx.State, ToolCallback> agentBuilder;

    public ReactAgentBuilderEx() {
        super();
        approvals = new LinkedHashMap<>();
        agentBuilder = AgentEx.builder();
    }

    public ReactAgentBuilderEx(ReactAgentBuilderEx<?, State> builder) {
        super(builder);
        this.agentBuilder = builder.agentBuilder;
        this.approvals = builder.approvals;
    }

    public B addCallModelHook(NodeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addCallModelHook(wrapCall);
        return result();
    }

    public B addDispatchToolsHook(NodeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addDispatchToolsHook(wrapCall);
        return result();
    }

    public B addApprovalActionHook(EdgeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addApprovalActionHook( wrapCall );
        return result();
    }

    public B addDispatchActionHook(EdgeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addDispatchActionHook( wrapCall );
        return result();
    }

    public B addShouldContinueHook(EdgeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addShouldContinueHook( wrapCall );
        return result();
    }

    public B approvalOn(String actionId, BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider  ) {
        var action = AgentEx.ApprovalNodeAction.<Message,State>builder()
                .interruptionMetadataProvider( interruptionMetadataProvider )
                .build();

        approvals.put( actionId, action  );
        return result();
    }
}
