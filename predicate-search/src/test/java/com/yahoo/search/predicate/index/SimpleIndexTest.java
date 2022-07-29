// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.search.predicate.serialization.SerializationTestHelper.assertSerializationDeserializationMatches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 * @author bjorncs
 */
public class SimpleIndexTest {

    private static final long KEY = 0x12345L;
    private static final int DOC_ID = 42;

    @Test
    void requireThatValuesCanBeInserted() {
        SimpleIndex.Builder builder = new SimpleIndex.Builder();
        builder.insert(KEY, new Posting(DOC_ID, 10));
        SimpleIndex index = builder.build();
        SimpleIndex.Entry e = index.getPostingList(KEY);
        assertNotNull(e);
        assertEquals(1, e.docIds.length);

        builder = new SimpleIndex.Builder();
        builder.insert(KEY, new Posting(DOC_ID, 10));
        builder.insert(KEY, new Posting(DOC_ID + 1, 20));
        index = builder.build();
        e = index.getPostingList(KEY);
        assertEquals(2, e.docIds.length);
        assertEquals(10, e.dataRefs[0]);
        assertEquals(20, e.dataRefs[1]);
    }

    @Test
    void requireThatEntriesAreSortedOnId() {
        SimpleIndex.Builder builder = new SimpleIndex.Builder();
        builder.insert(KEY, new Posting(DOC_ID, 10));
        builder.insert(KEY, new Posting(DOC_ID - 1, 20));  // Out of order
        builder.insert(KEY, new Posting(DOC_ID + 1, 30));
        SimpleIndex index = builder.build();
        SimpleIndex.Entry entry = index.getPostingList(KEY);
        assertEquals(3, entry.docIds.length);
        assertEquals(DOC_ID - 1, entry.docIds[0]);
        assertEquals(DOC_ID, entry.docIds[1]);
        assertEquals(DOC_ID + 1, entry.docIds[2]);
    }

    @Test
    void requireThatSerializationAndDeserializationRetainDictionary() throws IOException {
        SimpleIndex.Builder builder = new SimpleIndex.Builder();
        builder.insert(KEY, new Posting(DOC_ID, 10));
        builder.insert(KEY, new Posting(DOC_ID + 1, 20));
        builder.insert(KEY, new Posting(DOC_ID + 2, 30));
        builder.insert(KEY + 0xFFFFFF, new Posting(DOC_ID, 100));
        builder.insert(KEY + 0xFFFFFF, new Posting(DOC_ID + 1, 200));
        SimpleIndex index = builder.build();
        assertSerializationDeserializationMatches(index, SimpleIndex::writeToOutputStream, SimpleIndex::fromInputStream);
    }
}
