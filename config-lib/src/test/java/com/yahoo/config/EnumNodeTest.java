// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class EnumNodeTest {

    private static class MyNode extends EnumNode<MyNode.Enum> {
        public enum Enum { ONE, TWO }
        public final static Enum ONE = Enum.ONE;
        public final static Enum TWO = Enum.TWO;

        @Override
        protected boolean doSetValue(String name) {
          try {
            value = Enum.valueOf(name);
            return true;
          } catch (IllegalArgumentException e) {
          }
          return false;
        }

    }

    @Test
    void testEnumNode() {
        MyNode n = new MyNode();
        assertNull(n.getValue());
        assertEquals("(null)", n.toString());
        assertTrue(n.doSetValue("ONE"));
        assertEquals("ONE", n.getValue());
        assertEquals("ONE", n.toString());
        assertFalse(n.doSetValue("THREE"));
    }

}
