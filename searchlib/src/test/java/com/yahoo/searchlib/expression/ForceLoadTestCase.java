// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

public class ForceLoadTestCase extends junit.framework.TestCase {

    public ForceLoadTestCase(String name) {
        super(name);
    }

    public void testLoadClasses() {
        try {
            new com.yahoo.searchlib.expression.ForceLoad();
            assertTrue(com.yahoo.searchlib.expression.ForceLoad.forceLoad());
        } catch (com.yahoo.system.ForceLoadError e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
}
