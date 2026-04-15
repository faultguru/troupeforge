package com.troupeforge.core.agent;

import com.troupeforge.core.id.ContractCapabilityId;
import com.troupeforge.core.id.ToolId;

import java.util.Set;

public record DirectReturnPolicy(
    boolean enabled,
    Set<ToolId> eligibleTools,
    Set<ContractCapabilityId> eligibleContracts
) {}
