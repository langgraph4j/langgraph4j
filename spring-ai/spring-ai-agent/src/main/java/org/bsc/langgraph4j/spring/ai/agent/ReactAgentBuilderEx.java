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

    public B addNodeHook(NodeHook.BeforeCall<AgentExecutorEx.State> hook ) {
        agentBuilder.addNodeHook(hook);
        return result();
    }
    public B addNodeHook(NodeHook.WrapCall<AgentExecutorEx.State> hook ) {
        agentBuilder.addNodeHook(hook);
        return result();
    }
    public B addNodeHook(NodeHook.AfterCall<AgentExecutorEx.State> hook ) {
        agentBuilder.addNodeHook(hook);
        return result();
    }

    public B addEdgeHook(EdgeHook.BeforeCall<AgentExecutorEx.State> hook ) {
        agentBuilder.addEdgeHook(hook);
        return result();
    }
    public B addEdgeHook(EdgeHook.WrapCall<AgentExecutorEx.State> hook ) {
        agentBuilder.addEdgeHook(hook);
        return result();
    }
    public B addEdgeHook(EdgeHook.AfterCall<AgentExecutorEx.State> hook ) {
        agentBuilder.addEdgeHook(hook);
        return result();
    }

    public B addCallModelHook(NodeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addNodeHook( AgentEx.CALL_MODEL_NODE, wrapCall);
        return result();
    }

    public B addDispatchToolsHook(NodeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addNodeHook( AgentEx.ACTION_DISPATCHER_NODE, wrapCall);
        return result();
    }

    public B addApprovalActionHook(EdgeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addEdgeHook( AgentEx.APPROVAL_ACTION, wrapCall);
        return result();
    }

    public B addDispatchActionHook(EdgeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addEdgeHook( AgentEx.ACTION_DISPATCHER_NODE, wrapCall);
        return result();
    }

    public B addShouldContinueHook(EdgeHook.WrapCall<AgentExecutorEx.State> wrapCall ) {
        agentBuilder.addEdgeHook( AgentEx.CALL_MODEL_NODE, wrapCall);
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
