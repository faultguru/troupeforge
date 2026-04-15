package com.troupeforge.core.message;

import com.troupeforge.core.contract.ContractRef;
import com.troupeforge.core.id.AgentProfileId;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface MessageBus {
    <T extends Record> void send(MessageEnvelope<T> envelope);
    <I extends Record, O extends Record> CompletableFuture<MessageEnvelope<O>> request(
        MessageEnvelope<I> envelope, Duration timeout, Class<O> replyType);
    <T extends Record> void broadcast(MessageEnvelope<T> envelope);
    void subscribe(AgentProfileId profileId, MessageHandler handler);
    void subscribeByContract(ContractRef contractRef, MessageHandler handler);
    void subscribeBroadcast(String topic, MessageHandler handler);
}
