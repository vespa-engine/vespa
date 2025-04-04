package org.logstashplugins;

import org.junit.Test;

public class FeedConfigTest {

    @Test
    public void testValidateOperationAndCreate_ValidOperations() {
        // Test valid "put" operation
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "put", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception

        // Test valid "update" operation
        config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "update", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception

        // Test valid "remove" operation
        config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "remove", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_InvalidOperation() {
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "invalid", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_RemoveWithCreate() {
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "remove", true, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate();
    }

    @Test
    public void testValidateOperationAndCreate_DynamicOperation() {
        // When operation is dynamic, validation should be skipped
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "%{operation}", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception
    }
} 