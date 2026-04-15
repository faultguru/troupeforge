package com.troupeforge.tests;

import com.troupeforge.core.llm.LlmApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmApiExceptionTest {

    @Test
    void constructorWithStatusCodeSetsAllFields() {
        LlmApiException ex = new LlmApiException(503, "Service Unavailable", true);
        assertEquals(503, ex.statusCode());
        assertEquals("Service Unavailable", ex.responseBody());
        assertTrue(ex.isRetryable());
        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    void constructorWithMessageCauseSetsStatusCodeToNegativeOne() {
        RuntimeException cause = new RuntimeException("connection reset");
        LlmApiException ex = new LlmApiException("Network error", cause);
        assertEquals(-1, ex.statusCode());
        assertNull(ex.responseBody());
        assertFalse(ex.isRetryable());
        assertEquals("Network error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRateLimitedReturnsTrueFor429() {
        LlmApiException ex = new LlmApiException(429, "Too Many Requests", true);
        assertTrue(ex.isRateLimited());
    }

    @Test
    void isRateLimitedReturnsFalseForOtherCodes() {
        LlmApiException ex = new LlmApiException(500, "Server Error", false);
        assertFalse(ex.isRateLimited());
    }

    @Test
    void isServerErrorReturnsTrueFor500To599() {
        assertTrue(new LlmApiException(500, "", false).isServerError());
        assertTrue(new LlmApiException(502, "", false).isServerError());
        assertTrue(new LlmApiException(503, "", false).isServerError());
        assertTrue(new LlmApiException(599, "", false).isServerError());
    }

    @Test
    void isServerErrorReturnsFalseOutsideRange() {
        assertFalse(new LlmApiException(499, "", false).isServerError());
        assertFalse(new LlmApiException(600, "", false).isServerError());
        assertFalse(new LlmApiException(429, "", false).isServerError());
        assertFalse(new LlmApiException(200, "", false).isServerError());
    }

    @Test
    void isRetryableReflectsConstructorArg() {
        assertTrue(new LlmApiException(429, "rate limited", true).isRetryable());
        assertFalse(new LlmApiException(400, "bad request", false).isRetryable());
    }
}
