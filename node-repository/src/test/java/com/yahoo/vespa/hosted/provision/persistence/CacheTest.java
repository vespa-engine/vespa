// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.path.Path;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author mpolden
 */
public class CacheTest {

    @Test
    public void cache() {
        Cache<String> cache = new Cache<>(10);

        Path a = Path.fromString("/a");
        assertEquals("foo", cache.get(a, 1, () -> new Cache.Entry<>("foo", 1)).get());
        assertEquals("foo", cache.get(a, 1, () -> { throw new IllegalArgumentException("expected hit"); }).get());
        assertEquals("bar", cache.get(a, 2, () -> new Cache.Entry<>("bar", 2)).get());

        Path b = Path.fromString("/b");
        assertEquals("baz", cache.get(b, 2, () -> new Cache.Entry<>("baz", 2)).get());
    }

}
