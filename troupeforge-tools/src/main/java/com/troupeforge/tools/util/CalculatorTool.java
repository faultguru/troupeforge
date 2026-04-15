package com.troupeforge.tools.util;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

/**
 * Performs basic arithmetic operations.
 */
public class CalculatorTool implements Tool {

    public static final String NAME = "calculator";

    public record Request(
        @ToolParam(description = "Operation: add, subtract, multiply, or divide")
        String operation,
        @ToolParam(description = "First operand")
        Double a,
        @ToolParam(description = "Second operand")
        Double b
    ) {}

    public record Response(double result, String expression) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Perform basic arithmetic operations"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        String op = req.operation() != null ? req.operation().toLowerCase() : "";

        return switch (op) {
            case "add" -> new Response(req.a() + req.b(), req.a() + " + " + req.b() + " = " + (req.a() + req.b()));
            case "subtract" -> new Response(req.a() - req.b(), req.a() + " - " + req.b() + " = " + (req.a() - req.b()));
            case "multiply" -> new Response(req.a() * req.b(), req.a() + " * " + req.b() + " = " + (req.a() * req.b()));
            case "divide" -> {
                if (req.b() == 0.0) {
                    yield new Response(Double.NaN, "Error: division by zero");
                }
                yield new Response(req.a() / req.b(), req.a() + " / " + req.b() + " = " + (req.a() / req.b()));
            }
            default -> new Response(Double.NaN, "Error: unknown operation '" + op + "'. Use add, subtract, multiply, or divide.");
        };
    }
}
