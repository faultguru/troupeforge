package com.troupeforge.infra.messaging;

import com.troupeforge.core.contract.ContractRef;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.message.AgentAddress;
import com.troupeforge.core.message.MessageBus;
import com.troupeforge.core.message.MessageEnvelope;
import com.troupeforge.core.message.MessageHandler;
import com.troupeforge.core.message.MessageType;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class InMemoryMessageBus implements MessageBus {

    private final ConcurrentHashMap<String, MessageHandler> directSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MessageHandler> contractSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<MessageHandler>> broadcastSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<MessageEnvelope<?>>> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public <T extends Record> void send(MessageEnvelope<T> envelope) {
        AgentAddress recipient = envelope.recipient();
        MessageHandler handler = resolveHandler(recipient);
        if (handler != null) {
            handler.handle(envelope);
        }

        // If this is a reply, complete any pending request future
        if (envelope.type() == MessageType.REPLY && envelope.correlationId() != null) {
            CompletableFuture<MessageEnvelope<?>> future =
                    pendingRequests.remove(envelope.correlationId().value());
            if (future != null) {
                future.complete(envelope);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends Record, O extends Record> CompletableFuture<MessageEnvelope<O>> request(
            MessageEnvelope<I> envelope, Duration timeout, Class<O> replyType) {
        CompletableFuture<MessageEnvelope<?>> future = new CompletableFuture<>();
        String correlationKey = envelope.correlationId().value();
        pendingRequests.put(correlationKey, future);

        // Send the request
        send(envelope);

        return future
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(reply -> (MessageEnvelope<O>) reply)
                .whenComplete((result, ex) -> pendingRequests.remove(correlationKey));
    }

    @Override
    public <T extends Record> void broadcast(MessageEnvelope<T> envelope) {
        AgentAddress recipient = envelope.recipient();
        if (recipient instanceof AgentAddress.Broadcast broadcast) {
            List<MessageHandler> handlers = broadcastSubscribers.get(broadcast.topic());
            if (handlers != null) {
                for (MessageHandler handler : handlers) {
                    handler.handle(envelope);
                }
            }
        }
    }

    @Override
    public void subscribe(AgentProfileId profileId, MessageHandler handler) {
        directSubscribers.put(profileId.toKey(), handler);
    }

    @Override
    public void subscribeByContract(ContractRef contractRef, MessageHandler handler) {
        contractSubscribers.put(contractKey(contractRef), handler);
    }

    @Override
    public void subscribeBroadcast(String topic, MessageHandler handler) {
        broadcastSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    private static String contractKey(ContractRef ref) {
        return ref.id().value() + ":" + ref.version().major() + "." + ref.version().minor();
    }

    private MessageHandler resolveHandler(AgentAddress address) {
        if (address instanceof AgentAddress.Direct direct) {
            return directSubscribers.get(direct.profileId().toKey());
        } else if (address instanceof AgentAddress.ByContract byContract) {
            return contractSubscribers.get(contractKey(byContract.contractRef()));
        }
        return null;
    }
}
