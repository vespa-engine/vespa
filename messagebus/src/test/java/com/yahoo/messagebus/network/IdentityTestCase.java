// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class IdentityTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Identity id = new Identity("foo");
        assertNotNull(id.getHostname());
        assertEquals("foo", id.getServicePrefix());
    }

    @Test
    public void requireThatCopyConstructorWorks() {
        Identity lhs = new Identity("foo");
        Identity rhs = new Identity(lhs);
        assertEquals(lhs.getHostname(), rhs.getHostname());
        assertEquals(lhs.getServicePrefix(), rhs.getServicePrefix());
    }
}
