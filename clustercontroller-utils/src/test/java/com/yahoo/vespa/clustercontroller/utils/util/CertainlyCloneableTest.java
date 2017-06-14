// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import junit.framework.TestCase;

public class CertainlyCloneableTest extends TestCase {

    private class Foo extends CertainlyCloneable<Foo> {
        protected Foo callParentClone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException("Foo");
        }
    }

    public void testSimple() {
        try{
            Foo f = new Foo();
            f.clone();
            fail("Control should not get here");
        } catch (Error e) {
            assertEquals("Foo", e.getCause().getMessage());
        }
    }
}
