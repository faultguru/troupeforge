package com.troupeforge.core.llm;

/**
 * Exception thrown when an LLM provider API call fails.
 * Carries the HTTP status code and response body for structured error handling.
 */
public class LlmApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;
    private final boolean retryable;

    public LlmApiException(int statusCode, String responseBody, boolean retryable) {
        super("LLM API error: HTTP " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.retryable = retryable;
    }

    public LlmApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
        this.retryable = false;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Returns true if this was a rate limit error (HTTP 429).
     */
    public boolean isRateLimited() {
        return statusCode == 429;
    }

    /**
     * Returns true if this was a server error (5xx).
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }
}
