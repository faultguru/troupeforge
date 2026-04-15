package com.troupeforge.core.message;

public record ErrorPayload(String errorCode, String errorMessage, MessageId originalMessageId) {
}
