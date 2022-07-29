// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class CertainlyCloneableTest {

    private class Foo extends CertainlyCloneable<Foo> {
        protected Foo callParentClone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException("Foo");
        }
    }

    @Test
    void testSimple() {
        try {
            Foo f = new Foo();
            f.clone();
            fail("Control should not get here");
        } catch (Error e) {
            assertEquals("Foo", e.getCause().getMessage());
        }
    }

}
