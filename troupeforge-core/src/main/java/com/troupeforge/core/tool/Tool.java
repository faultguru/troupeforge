package com.troupeforge.core.tool;

/**
 * A tool that can be invoked by an agent during execution.
 * <p>
 * Each tool defines typed {@link Record} classes for its request and response.
 * The JSON schema for the LLM prompt is derived automatically from the request
 * record's components and {@link ToolParam} annotations.
 */
public interface Tool {
    String name();
    String description();
    Class<? extends Record> requestType();
    Class<? extends Record> responseType();
    Record execute(ToolContext context, Record request);

    default java.time.Duration timeout() { return java.time.Duration.ofSeconds(30); }

    /**
     * Optional instructions that get injected into the agent's system prompt
     * when this tool is assigned to an agent. Returns null if no instructions needed.
     * Lower priority values are placed earlier in the prompt.
     */
    default ToolInstruction toolInstruction() { return null; }
}
