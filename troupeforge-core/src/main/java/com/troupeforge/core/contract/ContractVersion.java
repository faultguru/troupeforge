package com.troupeforge.core.contract;

public record ContractVersion(int major, int minor) {
    public boolean isCompatibleWith(ContractVersion required) {
        return this.major == required.major && this.minor >= required.minor;
    }
}
