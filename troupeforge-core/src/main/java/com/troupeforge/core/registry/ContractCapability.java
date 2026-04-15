package com.troupeforge.core.registry;

import com.troupeforge.core.contract.ContractRef;
import com.troupeforge.core.message.ContractHandler;

public record ContractCapability<I extends Record, O extends Record>(
    ContractRef contractRef,
    ContractHandler<I, O> handler
) {}
