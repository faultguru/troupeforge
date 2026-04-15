package com.troupeforge.core.message;

import com.troupeforge.core.contract.ContractRef;
import com.troupeforge.core.id.AgentProfileId;

public sealed interface AgentAddress {
    record Direct(AgentProfileId profileId) implements AgentAddress {}
    record ByContract(ContractRef contractRef) implements AgentAddress {}
    record Broadcast(String topic) implements AgentAddress {}
}
