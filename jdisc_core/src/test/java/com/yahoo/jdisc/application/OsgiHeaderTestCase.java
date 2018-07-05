// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * @author Simon Thoresen Hult
 */
public class OsgiHeaderTestCase {

    @Test
    public void requireThatOsgiHeadersDoNotChange() {
        assertEquals("X-JDisc-Application", OsgiHeader.APPLICATION);
        assertEquals("X-JDisc-Preinstall-Bundle", OsgiHeader.PREINSTALL_BUNDLE);
        assertEquals("X-JDisc-Privileged-Activator", OsgiHeader.PRIVILEGED_ACTIVATOR);
    }
}
