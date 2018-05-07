// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ForceLoadTestCase {

    @Test
    public void testLoadClasses() {

        try {
            ForceLoad.forceLoad(getClass().getPackage().getName(), new String[] { "Foo", "Bar" },
                                this.getClass().getClassLoader());
        } catch (ForceLoadError e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void testLoadBogusClass() {
        try {
            ForceLoad.forceLoad(getClass().getPackage().getName(), new String[] { "Foo", "Bar", "Baz" },
                                this.getClass().getClassLoader());
        } catch (ForceLoadError e) {
            return;
        }
        assertTrue(false);
    }

}
