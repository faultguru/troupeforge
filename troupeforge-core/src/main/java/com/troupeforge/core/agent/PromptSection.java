package com.troupeforge.core.agent;

import java.util.List;

public record PromptSection(String key, List<String> content, int order) {
}
