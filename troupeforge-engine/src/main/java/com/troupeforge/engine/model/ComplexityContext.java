package com.troupeforge.engine.model;

import java.util.Map;

public record ComplexityContext(String taskDescription, int estimatedScope, Map<String, Object> hints) {
}
