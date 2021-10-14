// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Functional test of tag checking.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ServicePortTest {

    @Test
    public final void testNullTags() {
        ServicePort s = new ServicePort();
        s.tags = null;
        assertFalse(s.hasTags("http"));
    }

    @Test
    public final void testEmptyTags() {
        ServicePort s = new ServicePort();
        s.tags = "";
        assertFalse(s.hasTags("http"));
    }

    @Test
    public final void testSubsetInArgs() {
        ServicePort s = new ServicePort();
        s.tags = "http state status";
        assertTrue(s.hasTags("http", "state"));
    }

    @Test
    public final void testSupersetInArgs() {
        ServicePort s = new ServicePort();
        s.tags = "http";
        assertFalse(s.hasTags("http", "rpc"));
    }

    @Test
    public final void testIdenticalInArgs() {
        ServicePort s = new ServicePort();
        s.tags = "http rpc";
        assertTrue(s.hasTags("http", "rpc"));
    }

    @Test
    public final void testDisjunctBetweenArgsAndTags() {
        ServicePort s = new ServicePort();
        s.tags = "http state moo";
        assertFalse(s.hasTags("http", "state", "rpc"));
    }

    @Test
    public final void testTagNameStartsWithOther() {
        ServicePort s = new ServicePort();
        s.tags = "http state moo";
        assertFalse(s.hasTags("htt"));
    }

    @Test
    public final void testTagNameEndsWithOther() {
        ServicePort s = new ServicePort();
        s.tags = "http state moo";
        assertFalse(s.hasTags("tp"));
    }

    @Test
    public final void testTagNameIsSubstringofOther() {
        ServicePort s = new ServicePort();
        s.tags = "http state moo";
        assertFalse(s.hasTags("tt"));
    }

}
