// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Test of VirtualSourceResolver
 *
 * @author baldersheim
 */
public class VirtualSourceResolverTestCase {
    @Test
    void testThatOriginalIsReturnedIfNoMapping() {
        var input = Set.of("a","b", "b.c");
        assertSame(input, VirtualSourceResolver.of().resolve(input));
        assertSame(input, VirtualSourceResolver.of(Set.of("x.a","x.b")).resolve(input));
    }
    @Test
    void testResolution() {
        var input = Set.of("a","b", "b.c");
        assertEquals(Set.of("a.x", "a.y", "b.c", "b.x"),
                     VirtualSourceResolver.of(Set.of("a.x","a.y", "b.x")).resolve(input));
    }
}
