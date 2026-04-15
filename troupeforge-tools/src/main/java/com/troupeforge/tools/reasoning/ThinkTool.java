package com.troupeforge.tools.reasoning;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

/**
 * Structured thinking/reasoning scratchpad.
 * The value is that the thought appears in the conversation history as a tool call,
 * giving the agent a place to think without it being in the final response.
 */
public class ThinkTool implements Tool {

    public static final String NAME = "think";

    public record Request(
        @ToolParam(description = "Your reasoning, analysis, or thought process")
        String thought
    ) {}

    public record Response(boolean acknowledged) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Use this tool to think through problems step-by-step. Your thoughts will be recorded but not shown to the user."; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        return new Response(true);
    }
}
