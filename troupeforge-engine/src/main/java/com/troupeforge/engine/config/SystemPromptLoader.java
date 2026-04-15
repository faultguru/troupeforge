package com.troupeforge.engine.config;

import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.bucket.OrgConfigSource;

import java.util.List;

public interface SystemPromptLoader {
    List<PromptSection> loadSystemPrompts(OrgConfigSource configSource);
}
