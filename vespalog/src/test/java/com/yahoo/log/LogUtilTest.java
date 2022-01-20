// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
@SuppressWarnings("removal")
public class LogUtilTest {
    @Test
    public void testEmpty() {
        assertTrue(LogUtil.empty(null));
        assertTrue(LogUtil.empty(""));
        assertFalse(LogUtil.empty("f"));
        assertFalse(LogUtil.empty("fo"));
        assertFalse(LogUtil.empty("foo"));
    }
}
