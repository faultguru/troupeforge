package com.troupeforge.core.tool;

import com.troupeforge.core.id.ToolId;

import java.util.Map;

public record ToolBinding(ToolId toolId, Map<String, Object> defaultParameters, boolean inherited) {}
