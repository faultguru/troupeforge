package com.troupeforge.tests;

import com.troupeforge.client.SessionPersistence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionPersistenceTest {

    @Test
    void testSaveAndRetrieveSession() {
        SessionPersistence sp = new SessionPersistence();
        sp.saveSession("test-agent", "session-123");
        assertEquals("session-123", sp.getLastSession("test-agent"));
        // Cleanup
        sp.removeSession("test-agent");
    }

    @Test
    void testGetNonExistentSession() {
        SessionPersistence sp = new SessionPersistence();
        assertNull(sp.getLastSession("non-existent-agent-xyz"));
    }

    @Test
    void testRemoveSession() {
        SessionPersistence sp = new SessionPersistence();
        sp.saveSession("remove-test", "session-456");
        sp.removeSession("remove-test");
        assertNull(sp.getLastSession("remove-test"));
    }

    @Test
    void testOverwriteSession() {
        SessionPersistence sp = new SessionPersistence();
        sp.saveSession("overwrite-test", "session-1");
        sp.saveSession("overwrite-test", "session-2");
        assertEquals("session-2", sp.getLastSession("overwrite-test"));
        sp.removeSession("overwrite-test");
    }

    @Test
    void testNullHandling() {
        SessionPersistence sp = new SessionPersistence();
        assertNull(sp.getLastSession(null));
        // Should not throw
        sp.saveSession(null, "session-1");
        sp.removeSession(null);
    }
}
