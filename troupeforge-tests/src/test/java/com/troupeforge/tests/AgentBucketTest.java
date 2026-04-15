package com.troupeforge.tests;

import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.core.id.AgentId;
import com.troupeforge.core.id.AgentProfileId;
import com.troupeforge.core.id.PersonaId;
import com.troupeforge.engine.bucket.AgentBucket;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.tests.support.TestBucketHelper;
import com.troupeforge.tests.support.TestSpringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Agent Bucket Loading and Lifecycle")
class AgentBucketTest {

    @Autowired
    private AgentBucketRegistry bucketRegistry;

    @BeforeEach
    void setUp() {
        TestBucketHelper.loadTestBucket(bucketRegistry);
    }

    @Test
    void testLoadBucketFromFilesystemConfig() {
        AgentBucket bucket = bucketRegistry.getBucket(TestBucketHelper.TEST_BUCKET_ID);
        assertNotNull(bucket);
        assertEquals(TestBucketHelper.TEST_BUCKET_ID, bucket.bucketId());
        assertEquals(TestBucketHelper.TEST_ORG, bucket.organizationId());
        assertNotNull(bucket.loadedAt());
    }

    @Test
    void testBucketContainsAllExpectedAgents() {
        AgentBucket bucket = bucketRegistry.getBucket(TestBucketHelper.TEST_BUCKET_ID);

        Set<String> agentIds = bucket.resolvedAgents().keySet().stream()
                .map(AgentId::value)
                .collect(Collectors.toSet());

        assertTrue(agentIds.contains("root"), "Should contain root agent");
        assertTrue(agentIds.contains("greeter"), "Should contain greeter agent");
        assertTrue(agentIds.contains("dispatcher"), "Should contain dispatcher agent");
        assertTrue(agentIds.contains("architect"), "Should contain architect agent");
        assertTrue(agentIds.contains("researcher"), "Should contain researcher agent");
        assertTrue(agentIds.contains("mock-agent"), "Should contain mock-agent");
    }

    @Test
    void testBucketContainsAllExpectedProfiles() {
        AgentBucket bucket = bucketRegistry.getBucket(TestBucketHelper.TEST_BUCKET_ID);

        Set<String> profileKeys = bucket.agentProfiles().keySet().stream()
                .map(AgentProfileId::toKey)
                .collect(Collectors.toSet());

        // root has no persona — no root profile expected
        // greeter personas: simon, bond, lord
        assertTrue(profileKeys.contains("greeter:simon"), "Should contain greeter:simon profile");
        assertTrue(profileKeys.contains("greeter:bond"), "Should contain greeter:bond profile");
        assertTrue(profileKeys.contains("greeter:lord"), "Should contain greeter:lord profile");
        // dispatcher:linda
        assertTrue(profileKeys.contains("dispatcher:linda"), "Should contain dispatcher:linda profile");
        // architect: sofia, martin
        assertTrue(profileKeys.contains("architect:sofia"), "Should contain architect:sofia profile");
        assertTrue(profileKeys.contains("architect:martin"), "Should contain architect:martin profile");
        // researcher: guru, nick
        assertTrue(profileKeys.contains("researcher:guru"), "Should contain researcher:guru profile");
        assertTrue(profileKeys.contains("researcher:nick"), "Should contain researcher:nick profile");
        // mock-agent: clara, max
        assertTrue(profileKeys.contains("mock-agent:clara"), "Should contain mock-agent:clara profile");
        assertTrue(profileKeys.contains("mock-agent:max"), "Should contain mock-agent:max profile");
    }

    @Test
    void testBucketIsolation_DifferentOrgsCannotSeeEachOther() {
        // The test bucket is loaded for TEST_ORG. A different org should not have a bucket.
        AgentBucketId otherBucketId = AgentBucketId.of(
                new com.troupeforge.core.id.OrganizationId("other-org"),
                com.troupeforge.core.context.StageContext.LIVE);

        assertThrows(IllegalStateException.class,
                () -> bucketRegistry.getBucket(otherBucketId),
                "Should not find a bucket for a different org");
    }

    @Test
    void testBucketIdDerivedFromOrgAndStage() {
        AgentBucketId bucketId = AgentBucketId.of(TestBucketHelper.TEST_ORG, TestBucketHelper.TEST_STAGE);
        assertEquals("test-org:live", bucketId.value());
    }
}
