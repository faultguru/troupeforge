package com.troupeforge.core.agent;

public record PersonaSectionDefinition(
    String key,
    String description,
    int order,
    boolean required
) {}
