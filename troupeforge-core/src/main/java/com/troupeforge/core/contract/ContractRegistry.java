package com.troupeforge.core.contract;

import com.troupeforge.core.id.AgentBucketId;

import java.util.*;

public interface ContractRegistry {
    void register(AgentBucketId bucket, ContractDefinition contract);
    Optional<ContractDefinition> find(AgentBucketId bucket, ContractId id, ContractVersion version);
    Optional<ContractDefinition> findLatest(AgentBucketId bucket, ContractId id);
    Collection<ContractDefinition> all(AgentBucketId bucket);
    List<ContractDefinition> allVersions(AgentBucketId bucket, ContractId id);
}
