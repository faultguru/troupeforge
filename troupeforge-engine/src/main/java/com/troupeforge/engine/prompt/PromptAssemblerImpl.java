package com.troupeforge.engine.prompt;

import com.troupeforge.core.agent.PromptSection;
import com.troupeforge.core.context.RequestContext;
import com.troupeforge.core.persona.AgentProfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link PromptAssembler} that joins prompt sections
 * into a single system prompt string.
 */
public class PromptAssemblerImpl implements PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(PromptAssemblerImpl.class);

    @Override
    public String assemble(RequestContext requestContext, AgentProfile profile) {
        List<PromptSection> sections = profile.effectivePromptSections();
        if (sections == null || sections.isEmpty()) {
            log.info("Prompt assembled for agent: profileId={} (empty)", profile.profileId().toKey());
            return "";
        }

        String sectionKeys = sections.stream()
                .map(s -> s.key() + "(order=" + s.order() + ")")
                .collect(Collectors.joining(", "));

        // Prepend agent identity so the agent knows who it is
        String displayName = profile.effectiveDisplayName();
        String personaId = profile.profileId().personaId().value();
        String identity = "Your name is " + (displayName != null ? displayName : personaId)
                + ". Your persona ID is " + personaId + ".";

        String result = identity + "\n\n" + sections.stream()
                .map(section -> String.join("\n", section.content()))
                .collect(Collectors.joining("\n\n"));

        log.info("Prompt assembled: profileId={}, sections=[{}], length={}",
                profile.profileId().toKey(), sectionKeys, result.length());

        return result;
    }
}
