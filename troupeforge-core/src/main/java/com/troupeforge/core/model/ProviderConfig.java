package com.troupeforge.core.model;

import java.util.List;
import java.util.Map;

public record ProviderConfig(
    String id,
    String name,
    String type,
    String baseUrl,
    AuthConfig auth,
    List<String> supportedModels,
    Map<String, String> headers
) {
    public record AuthConfig(
        String method,
        List<String> credentialPaths
    ) {}
}
