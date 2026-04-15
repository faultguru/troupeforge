package com.troupeforge.tools.delegation;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolInstruction;
import com.troupeforge.core.tool.ToolParam;

import java.util.List;

public class HandoverToAgentTool implements Tool {

    public static final String NAME = "handover_to_agent";

    public record Request(
        @ToolParam(description = "The unique persona ID of the agent to hand over to")
        String personaId,
        @ToolParam(description = "The message or context to pass to the target agent")
        String message
    ) {}

    public record Response(
        @ToolParam(description = "The result returned by the target agent")
        String result
    ) {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Transfer the conversation to another agent. The response goes directly to the original requester.";
    }

    @Override
    public Class<Request> requestType() {
        return Request.class;
    }

    @Override
    public Class<Response> responseType() {
        return Response.class;
    }

    @Override
    public Record execute(ToolContext context, Record request) {
        // Actual handover is handled by the executor, which intercepts this tool name.
        var req = (Request) request;
        return new Response(req.message());
    }

    @Override
    public ToolInstruction toolInstruction() {
        return new ToolInstruction(25, List.of(
                ToolInstruction.line("## Handover Instructions"),
                ToolInstruction.line("You can permanently transfer the conversation to another agent using the handover_to_agent tool."),
                ToolInstruction.line("Unlike delegation, handover means you are stepping out — the target agent takes over completely."),
                ToolInstruction.line("Use handover when the user's request is better served by a different agent entirely."),
                ToolInstruction.line("First call list_agents to find the target agent's personaId, then call handover_to_agent."),
                ToolInstruction.line("The target agent's response goes directly to the user — you do not see or relay it.")
        ));
    }
}
