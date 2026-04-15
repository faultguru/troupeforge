package com.troupeforge.engine.execution;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.id.AgentSessionId;

import java.util.concurrent.CompletableFuture;

public interface AgentExecutor {
    CompletableFuture<ExecutionResult> execute(RequestContext requestContext, AgentSessionId sessionId, String message);
}
