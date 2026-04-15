package com.troupeforge.infra.storage;

import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.storage.DocumentStore;
import com.troupeforge.core.storage.QueryCriteria;
import com.troupeforge.core.storage.Storable;
import com.troupeforge.core.storage.StorageResult;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDocumentStore<T extends Storable> implements DocumentStore<T> {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, StorageResult<T>>> store =
            new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, StorageResult<T>> bucketMap(AgentBucketId bucket) {
        return store.computeIfAbsent(bucket.value(), k -> new ConcurrentHashMap<>());
    }

    @Override
    public StorageResult<T> get(AgentBucketId bucket, String id) {
        StorageResult<T> result = bucketMap(bucket).get(id);
        if (result == null) {
            throw new NoSuchElementException(
                    "Document not found: bucket=" + bucket.value() + ", id=" + id);
        }
        return result;
    }

    @Override
    public Optional<StorageResult<T>> findById(AgentBucketId bucket, String id) {
        return Optional.ofNullable(bucketMap(bucket).get(id));
    }

    @Override
    public List<StorageResult<T>> list(AgentBucketId bucket) {
        return List.copyOf(bucketMap(bucket).values());
    }

    @Override
    public List<StorageResult<T>> query(AgentBucketId bucket, QueryCriteria criteria) {
        var values = bucketMap(bucket).values().stream();
        if (criteria.offset() > 0) {
            values = values.skip(criteria.offset());
        }
        if (criteria.limit() > 0) {
            values = values.limit(criteria.limit());
        }
        return values.toList();
    }

    @Override
    public StorageResult<T> put(AgentBucketId bucket, T entity) {
        ConcurrentHashMap<String, StorageResult<T>> map = bucketMap(bucket);
        StorageResult<T> existing = map.get(entity.id());
        long nextVersion = (existing != null) ? existing.version() + 1 : 1;
        StorageResult<T> result = new StorageResult<>(entity, nextVersion, Instant.now());
        map.put(entity.id(), result);
        return result;
    }

    @Override
    public boolean delete(AgentBucketId bucket, String id, long expectedVersion) {
        ConcurrentHashMap<String, StorageResult<T>> map = bucketMap(bucket);
        StorageResult<T> existing = map.get(id);
        if (existing == null || existing.version() != expectedVersion) {
            return false;
        }
        return map.remove(id, existing);
    }
}
