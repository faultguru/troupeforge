package com.troupeforge.core.message;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ContractHandler<I extends Record, O extends Record> {
    CompletableFuture<O> handle(I input, MessageEnvelope<I> envelope);
}
