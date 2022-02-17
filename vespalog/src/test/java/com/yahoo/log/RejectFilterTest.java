// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
@SuppressWarnings("removal")
public class RejectFilterTest {
    @Test
    public void testBasicPatternMatching() {
        RejectFilter rejectFilter = new RejectFilter();
        assertFalse(rejectFilter.shouldReject("This is a test"));
        rejectFilter.addRejectedMessage("This is a test");
        assertTrue(rejectFilter.shouldReject("This is a test"));
        rejectFilter.addRejectedMessage("This is not a test");
        assertTrue(rejectFilter.shouldReject("This is a test"));
        assertTrue(rejectFilter.shouldReject("This is not a test"));
        assertFalse(rejectFilter.shouldReject("This is not not a test"));
        assertFalse(rejectFilter.shouldReject(null));
    }

    @Test
    public void testDefaultRejectPattern() {
        RejectFilter filter = RejectFilter.createDefaultRejectFilter();
        assertTrue(filter.shouldReject("E 23-235018.067240 14650 23/10/2012 23:50:18 yjava_preload.so: [preload.c:350] Using FILTER_NONE:  This must be paranoid approved, and since you are using FILTER_NONE you must live with this error."));
    }
}
