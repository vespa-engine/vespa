// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
public class DataTypeNameTestCase {

    @Test
    public void requireThatAccessorsWork() {
        DataTypeName name = new DataTypeName("foo");
        assertEquals("foo", name.getName());
        assertEquals("foo", name.toString());
        assertFalse(name.equals(new Object()));
    }

}
