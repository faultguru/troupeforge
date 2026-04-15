package com.troupeforge.core.agent;

import java.util.List;

public record InheritablePromptSections(InheritanceAction action, List<PromptSection> sections) {
}
