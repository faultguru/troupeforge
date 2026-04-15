package com.troupeforge.core.storage;

import com.troupeforge.core.id.AgentBucketId;

import java.util.List;
import java.util.Optional;

public interface DocumentStore<T extends Storable> {
    StorageResult<T> get(AgentBucketId bucket, String id);
    Optional<StorageResult<T>> findById(AgentBucketId bucket, String id);
    List<StorageResult<T>> list(AgentBucketId bucket);
    List<StorageResult<T>> query(AgentBucketId bucket, QueryCriteria criteria);
    StorageResult<T> put(AgentBucketId bucket, T entity);
    boolean delete(AgentBucketId bucket, String id, long expectedVersion);
}
