// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author Tony Vaagenes
 */
public class ChainTest {

    public static class Filter {

    }

    public static class OtherFilter extends Filter {

    }

    @Test
    public void empty_chain_toString() {
        Chain<Filter> c = new Chain<>("myChain");
        assertEquals("chain 'myChain'{}", c.toString());
    }

    @Test
    public void singleton_chain_toString() {
        Chain<Filter> c = new Chain<>("myChain", new Filter());
        assertEquals("chain 'myChain'{ Filter }", c.toString());
    }

    @Test
    public void chain_toString() {
        Chain<Filter> c = new Chain<>("myChain", new Filter(), new Filter(), new OtherFilter());
        assertEquals("chain 'myChain'{ Filter -> Filter -> OtherFilter }", c.toString());
    }

    @Test
    public void non_equal_due_to_different_components() {
        assertNotEquals(new Chain<>("a", new Filter()), new Chain<>("a", new Filter()));
    }

    @Test
    public void non_equal_due_to_different_size_comopnents() {
        assertNotEquals(new Chain<>("a", new Filter()), new Chain<Filter>("a"));
    }

    @Test
    public void hashCode_equals() {
        assertEquals(new Chain<>("a").hashCode(), new Chain<Filter>("a").hashCode());
    }
}
