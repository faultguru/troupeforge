package com.troupeforge.core.registry;

import com.troupeforge.core.contract.ContractRef;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentProfileId;

import java.util.List;
import java.util.Optional;

public interface AgentRegistry {
    void register(AgentBucketId bucket, AgentDescriptor descriptor);
    Optional<AgentDescriptor> findAgent(AgentBucketId bucket, AgentProfileId profileId);
    List<AgentDescriptor> findProviders(AgentBucketId bucket, ContractRef contractRef);
}
