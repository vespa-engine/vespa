// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * @author lulf
 */
public class HostNameTestCase {

    @Test
    public void testHostnameIsFound() {
        assertFalse(HostName.getLocalhost().isEmpty());
    }

    @Test
    public void testSystemHostnameIsFound() throws Exception {
        assertFalse(HostName.getSystemHostName().isEmpty());
    }

}
