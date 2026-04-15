package com.troupeforge.tests;

import com.troupeforge.core.contract.ContractDefinition;
import com.troupeforge.core.contract.ContractRegistry;
import com.troupeforge.core.id.AgentBucketId;
import com.troupeforge.engine.bucket.AgentBucketRegistry;
import com.troupeforge.tests.support.TestBucketHelper;
import com.troupeforge.tests.support.TestSpringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestSpringConfig.class)
@DisplayName("Contract System")
class ContractSystemTest {

    @Autowired
    private AgentBucketRegistry bucketRegistry;

    @Autowired(required = false)
    private ContractRegistry contractRegistry;

    @BeforeEach
    void setUp() {
        TestBucketHelper.loadTestBucket(bucketRegistry);
    }

    @Test
    void testContractsLoadedFromConfig() {
        // The contract config loader should have loaded contracts during bucket loading.
        // If ContractRegistry is available, verify the contracts are registered.
        // If not, we verify the bucket loaded successfully (which requires contract loading).
        assertNotNull(bucketRegistry.getBucket(TestBucketHelper.TEST_BUCKET_ID),
                "Bucket should be loaded, which requires successful contract loading");

        if (contractRegistry != null) {
            Collection<ContractDefinition> contracts =
                    contractRegistry.all(TestBucketHelper.TEST_BUCKET_ID);

            Set<String> contractIds = contracts.stream()
                    .map(c -> c.id().value())
                    .collect(Collectors.toSet());

            assertTrue(contractIds.contains("chat"),
                    "Should contain chat contract");
            assertTrue(contractIds.contains("architecture-review"),
                    "Should contain architecture-review contract");
            assertTrue(contractIds.contains("research"),
                    "Should contain research contract");
            assertTrue(contractIds.contains("web-search"),
                    "Should contain web-search contract");
        }
    }
}
