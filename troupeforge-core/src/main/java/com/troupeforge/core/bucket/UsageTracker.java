package com.troupeforge.core.bucket;

public interface UsageTracker {
    void record(UsageEvent event);
}
