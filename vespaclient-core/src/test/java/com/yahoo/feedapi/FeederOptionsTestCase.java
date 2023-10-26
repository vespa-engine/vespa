// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class FeederOptionsTestCase {

    @Test
    public void testEqualsAndHashCode() {
        FeederOptions f1 = new FeederOptions();
        FeederOptions f2 = new FeederOptions();
        assertTrue(f1.equals(f2));
        assertTrue(f2.equals(f1));
        assertTrue(f1.hashCode() == f2.hashCode());

        f1.setAbortOnDocumentError(false);
        assertFalse(f1.equals(f2));
        assertFalse(f2.equals(f1));
        assertFalse(f1.hashCode() == f2.hashCode());

        f1.setAbortOnDocumentError(true);
        assertTrue(f1.equals(f2));
        assertTrue(f2.equals(f1));
        assertTrue(f1.hashCode() == f2.hashCode());

        f1.setRoute("all roads lead to rome");
        assertFalse(f1.equals(f2));
        assertFalse(f2.equals(f1));
        assertFalse(f1.hashCode() == f2.hashCode());

        f2.setRoute("all roads lead to trondheim");
        assertFalse(f1.equals(f2));
        assertFalse(f2.equals(f1));
        assertFalse(f1.hashCode() == f2.hashCode());

        f2.setRoute("all roads lead to rome");
        assertTrue(f1.equals(f2));
        assertTrue(f2.equals(f1));
        assertTrue(f1.hashCode() == f2.hashCode());

        f1.setTraceLevel(0);
        assertTrue(f1.equals(f2));
        assertTrue(f2.equals(f1));
        assertTrue(f1.hashCode() == f2.hashCode());

        f2.setTraceLevel(0);
        assertTrue(f1.equals(f2));
        assertTrue(f2.equals(f1));
        assertTrue(f1.hashCode() == f2.hashCode());

        f1.setTraceLevel(5);
        assertFalse(f1.equals(f2));
        assertFalse(f2.equals(f1));
        assertFalse(f1.hashCode() == f2.hashCode());

        f2.setTraceLevel(5);
        assertTrue(f1.equals(f2));
        assertTrue(f2.equals(f1));
        assertTrue(f1.hashCode() == f2.hashCode());

        f2.setMaxFeedRate(34.0);
        assertFalse(f2.equals(f1));
        assertFalse(f1.equals(f2));
        assertTrue(f1.hashCode() != f2.hashCode());
    }

}
