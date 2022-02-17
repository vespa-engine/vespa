// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.impl;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author Ulf Lilleengen
 * @author Bjorn Borud
 */
public class LogUtilsTest {

    @Test
    public void testEmpty() {
        assertTrue(LogUtils.empty(null));
        assertTrue(LogUtils.empty(""));
        assertFalse(LogUtils.empty("f"));
        assertFalse(LogUtils.empty("fo"));
        assertFalse(LogUtils.empty("foo"));
    }

    /**
     * Just make sure the static getHostName() method returns something
     * that looks half sensible.
     */
    @Test
    public void testSimple () {
        String name = LogUtils.getHostName();
        assertNotNull(name);
        assertFalse(name.equals(""));
    }

}
