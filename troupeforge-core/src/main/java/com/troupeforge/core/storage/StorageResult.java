package com.troupeforge.core.storage;

import java.time.Instant;

public record StorageResult<T>(T entity, long version, Instant lastModified) {}
