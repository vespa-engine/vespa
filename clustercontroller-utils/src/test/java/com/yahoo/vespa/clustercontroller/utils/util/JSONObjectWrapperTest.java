// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import junit.framework.TestCase;

public class JSONObjectWrapperTest extends TestCase {

    public void testExceptionWrapping() {
        JSONObjectWrapper wrapper = new JSONObjectWrapper();
        try{
            wrapper.put(null, "foo");
        } catch (NullPointerException e) {
            assertEquals("Null key.", e.getMessage());
        }
    }
}
