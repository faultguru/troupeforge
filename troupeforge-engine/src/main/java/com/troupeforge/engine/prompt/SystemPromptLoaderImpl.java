package com.troupeforge.engine.prompt;

import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.engine.config.SystemPromptLoader;

import java.util.Collections;
import java.util.List;

/**
 * System prompts are now part of the root agent's promptSections and are
 * resolved through agent inheritance. This loader returns an empty list.
 */
public class SystemPromptLoaderImpl implements SystemPromptLoader {

    @Override
    public List<PromptSection> loadSystemPrompts(OrgConfigSource configSource) {
        return Collections.emptyList();
    }
}
