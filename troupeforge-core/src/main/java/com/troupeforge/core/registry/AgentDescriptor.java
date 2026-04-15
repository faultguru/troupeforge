package com.troupeforge.core.registry;

import com.troupeforge.core.contract.ContractRef;
import com.troupeforge.core.id.AgentProfileId;

import java.util.List;

public record AgentDescriptor(
    AgentProfileId profileId,
    String displayName,
    List<ContractCapability<?, ?>> provides,
    List<ContractRef> requires
) {}
