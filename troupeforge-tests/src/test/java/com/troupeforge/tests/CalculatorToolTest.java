package com.troupeforge.tests;

import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.context.RequestorContext;
import com.troupeforge.core.context.StageContext;
import com.troupeforge.core.id.*;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.tools.util.CalculatorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CalculatorTool standalone")
class CalculatorToolTest {

    @TempDir
    Path tempDir;

    private CalculatorTool tool;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        tool = new CalculatorTool();
        RequestContext reqCtx = new RequestContext(
                new RequestId("req-calc"),
                new RequestorContext(new UserId("test-user"), new OrganizationId("test-org")),
                StageContext.LIVE,
                Instant.now()
        );
        toolContext = new ToolContext(
                reqCtx,
                new AgentSessionId("session-calc"),
                new AgentProfileId(new AgentId("agent-calc"), new PersonaId("test")),
                tempDir,
                Map.of()
        );
    }

    @Test
    @DisplayName("Addition returns correct result and expression")
    void testAddition() {
        var resp = (CalculatorTool.Response) tool.execute(toolContext, new CalculatorTool.Request("add", 2.5, 3.5));
        assertEquals(6.0, resp.result(), 0.001);
        assertTrue(resp.expression().contains("6.0"));
    }

    @Test
    @DisplayName("Subtraction returns correct result")
    void testSubtraction() {
        var resp = (CalculatorTool.Response) tool.execute(toolContext, new CalculatorTool.Request("subtract", 10.0, 4.0));
        assertEquals(6.0, resp.result(), 0.001);
    }

    @Test
    @DisplayName("Multiplication returns correct result")
    void testMultiplication() {
        var resp = (CalculatorTool.Response) tool.execute(toolContext, new CalculatorTool.Request("multiply", 3.0, 7.0));
        assertEquals(21.0, resp.result(), 0.001);
    }

    @Test
    @DisplayName("Division by zero returns NaN and error expression")
    void testDivisionByZero() {
        var resp = (CalculatorTool.Response) tool.execute(toolContext, new CalculatorTool.Request("divide", 5.0, 0.0));
        assertTrue(Double.isNaN(resp.result()));
        assertTrue(resp.expression().contains("Error"));
        assertTrue(resp.expression().contains("division by zero"));
    }

    @Test
    @DisplayName("Unknown operation returns NaN and error message")
    void testUnknownOperation() {
        var resp = (CalculatorTool.Response) tool.execute(toolContext, new CalculatorTool.Request("modulus", 10.0, 3.0));
        assertTrue(Double.isNaN(resp.result()));
        assertTrue(resp.expression().contains("Error"));
        assertTrue(resp.expression().contains("modulus"));
    }
}
