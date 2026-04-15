package com.troupeforge.tools.delegation;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolInstruction;
import com.troupeforge.core.tool.ToolParam;

import java.util.List;

public class DelegateToAgentTool implements Tool {

    public static final String NAME = "delegate_to_agent";

    public record Request(
        @ToolParam(description = "The unique persona ID of the agent to delegate to")
        String personaId,
        @ToolParam(description = "The message or task to send to the delegated agent")
        String message
    ) {}

    public record Response(
        @ToolParam(description = "The result returned by the delegated agent")
        String result
    ) {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Delegate a task to another agent and wait for the response";
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
        // Actual delegation is handled by the executor, which intercepts this tool name.
        var req = (Request) request;
        return new Response(req.message());
    }

    @Override
    public ToolInstruction toolInstruction() {
        return new ToolInstruction(20, List.of(
                ToolInstruction.line("## Delegation Instructions"),
                ToolInstruction.line("You can delegate tasks to other agents using the delegate_to_agent tool."),
                ToolInstruction.line("When the user mentions another agent by name or asks you to talk to, ask, or check with someone, you MUST first call list_agents to find their personaId, then call delegate_to_agent with that personaId."),
                ToolInstruction.line("IMPORTANT: Always call list_agents first when the user references any agent — never guess a personaId."),
                ToolInstruction.line("If the user says something like 'ask lord how he is' or 'talk to bond', call list_agents with the name as filter, then delegate_to_agent with the discovered personaId."),
                ToolInstruction.line("After receiving the delegated response, relay it to the user in your own voice and style."),
                ToolInstruction.line("You remain the primary agent — delegation is invisible to the user except for the content."),
                ToolInstruction.whenAbsent(HandoverToAgentTool.NAME, "You do NOT have the ability to hand over or transfer conversations to other agents. You can only delegate tasks and relay responses. Never tell the user you are transferring or connecting them to another agent — you stay in control at all times.")
        ));
    }
}
