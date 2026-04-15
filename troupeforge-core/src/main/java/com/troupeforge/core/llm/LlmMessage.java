package com.troupeforge.core.llm;

import java.util.*;

public record LlmMessage(MessageRole role, List<MessageContent> content) {
}
