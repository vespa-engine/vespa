// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.search.searchers.ChunkLimitingSearcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Test ChunkLimitingSearcher
///
/// @author andreer
public class ChunkLimitingSearcherTestCase {
    @Test
    void testStuff() {
        ChunkLimitingSearcher chunkLimitingSearcher = new ChunkLimitingSearcher();

        assertNotNull(chunkLimitingSearcher);
    }
}
