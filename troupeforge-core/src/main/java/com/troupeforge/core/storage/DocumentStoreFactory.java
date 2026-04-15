package com.troupeforge.core.storage;

public interface DocumentStoreFactory {
    <T extends Storable> DocumentStore<T> create(String collection, Class<T> type);
}
