package com.troupeforge.infra.llm;

import java.util.List;

public record ClaudeProviderConfig(String baseUrl, List<String> credentialPaths) {
}
