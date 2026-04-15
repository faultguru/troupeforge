package com.troupeforge.core.message;

@FunctionalInterface
public interface MessageHandler {
    void handle(MessageEnvelope<?> envelope);
}
