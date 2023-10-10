// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class DocumentRemoveTestCase {

    @Test
    public void requireThatToStringWorks() {
        DocumentId docId = new DocumentId("id:this:is::a:test");
        DocumentRemove r = new DocumentRemove(docId);
        assertTrue(r.toString().contains(docId.toString()));
    }

    @Test
    public void requireThatEqualsAndHashCodeWorks() {
        DocumentRemove r1 = new DocumentRemove(new DocumentId("id:this:is::a:test"));
        DocumentRemove r2 = new DocumentRemove(new DocumentId("id:this:is::a:test"));
        DocumentRemove r3 = new DocumentRemove(new DocumentId("id:this:is::nonequal"));

        assertEquals(r1, r1);
        assertEquals(r1, r2);
        assertEquals(r2, r1);
        assertEquals(r1.hashCode(), r2.hashCode());

        assertNotEquals(r1, r3);
        assertNotEquals(r3, r1);
        assertNotEquals(r2, r3);
        assertNotEquals(r3, r2);
        assertNotEquals(r1.hashCode(), r3.hashCode());

        assertNotEquals(r1, new Object());
        assertFalse(r1.equals("banana"));
    }
}
