// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.test;

import com.yahoo.prelude.fastsearch.FastHit;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class FastHitTestCase {

    @Test
    public void requireThatIgnoreRowBitsIsFalseByDefault() {
        FastHit hit = new FastHit();
        assertFalse(hit.shouldIgnoreRowBits());
    }

    @Test
    public void requireThatIgnoreRowBitsCanBeSet() {
        FastHit hit = new FastHit();
        hit.setIgnoreRowBits(true);
        assertTrue(hit.shouldIgnoreRowBits());
    }
}
