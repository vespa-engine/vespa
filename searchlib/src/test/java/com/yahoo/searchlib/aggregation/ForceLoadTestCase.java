// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ForceLoadTestCase {

    @Test
    public void testLoadClasses() {
        try {
            new com.yahoo.searchlib.aggregation.ForceLoad();
            assertTrue(com.yahoo.searchlib.aggregation.ForceLoad.forceLoad());
        } catch (com.yahoo.system.ForceLoadError e) {
            e.printStackTrace();
            fail("Load failed");
        }
    }

}
