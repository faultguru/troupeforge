package com.troupeforge.core.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Instructions that a tool contributes to the agent's system prompt.
 * Lower priority values are placed earlier in the prompt.
 * <p>
 * Lines can be unconditional (always included) or conditional on the presence
 * or absence of another tool in the agent's tool set.
 *
 * @param priority ordering priority (lower = earlier in prompt)
 * @param lines instruction lines with optional conditions
 */
public record ToolInstruction(int priority, List<Line> lines) {

    /**
     * A single instruction line, optionally conditional on a tool's presence.
     *
     * @param text the instruction text
     * @param condition null = always include, otherwise evaluated against the agent's tool set
     */
    public record Line(String text, Condition condition) {
        public Line(String text) {
            this(text, null);
        }
    }

    /**
     * Condition on a tool's presence in the agent's assigned tools.
     *
     * @param toolName the tool name to check
     * @param present true = include line when tool IS present, false = include when tool is ABSENT
     */
    public record Condition(String toolName, boolean present) {
        public static Condition whenPresent(String toolName) { return new Condition(toolName, true); }
        public static Condition whenAbsent(String toolName) { return new Condition(toolName, false); }
    }

    public ToolInstruction {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("ToolInstruction must have at least one line");
        }
    }

    /**
     * Resolve conditional lines against the agent's available tool names
     * and return the final prompt text.
     */
    public String toPromptText(Set<String> availableTools) {
        List<String> resolved = new ArrayList<>();
        for (Line line : lines) {
            if (line.condition() == null) {
                resolved.add(line.text());
            } else {
                boolean toolExists = availableTools.contains(line.condition().toolName());
                if (line.condition().present() == toolExists) {
                    resolved.add(line.text());
                }
            }
        }
        return String.join("\n", resolved);
    }

    /**
     * Convenience: render without conditions (all unconditional lines).
     */
    public String toPromptText() {
        return toPromptText(Set.of());
    }

    // --- Factory helpers for cleaner construction ---

    /** Unconditional line. */
    public static Line line(String text) {
        return new Line(text);
    }

    /** Line included only when a specific tool IS present. */
    public static Line whenPresent(String toolName, String text) {
        return new Line(text, Condition.whenPresent(toolName));
    }

    /** Line included only when a specific tool is ABSENT. */
    public static Line whenAbsent(String toolName, String text) {
        return new Line(text, Condition.whenAbsent(toolName));
    }
}
