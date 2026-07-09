// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author boeker
 */
public class DocumentCountTest {

    @Test
    void verifyDefaults() {
        DocumentCount count = new DocumentCount();
        assertEquals(0, count.getActiveDocuments());
        assertEquals(0, count.getTargetActiveDocuments());
        assertFalse(count.isReliable());
    }

    @Test
    void documentCountsCanBeRetrieved() {
        DocumentCount reliable = new DocumentCount(23, 42, true);
        assertEquals(23, reliable.getActiveDocuments());
        assertEquals(42, reliable.getTargetActiveDocuments());
        assertTrue(reliable.isReliable());

        DocumentCount unreliable = new DocumentCount(123, 456, false);
        assertEquals(123, unreliable.getActiveDocuments());
        assertEquals(456, unreliable.getTargetActiveDocuments());
        assertFalse(unreliable.isReliable());
    }
}
