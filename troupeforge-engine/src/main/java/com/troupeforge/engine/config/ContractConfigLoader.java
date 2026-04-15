package com.troupeforge.engine.config;

import com.troupeforge.core.bucket.OrgConfigSource;
import com.troupeforge.core.contract.ContractDefinition;

import java.util.List;

public interface ContractConfigLoader {
    List<ContractDefinition> loadContracts(OrgConfigSource configSource);
}
