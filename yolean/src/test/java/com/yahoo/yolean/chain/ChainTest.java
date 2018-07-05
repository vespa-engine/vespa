// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

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
        assertThat(c.toString(), is("chain 'myChain'{}"));
    }

    @Test
    public void singleton_chain_toString() {
        Chain<Filter> c = new Chain<>("myChain", new Filter());
        assertThat(c.toString(), is("chain 'myChain'{ Filter }"));
    }

    @Test
    public void chain_toString() {
        Chain<Filter> c = new Chain<>("myChain", new Filter(), new Filter(), new OtherFilter());
        assertThat(c.toString(), is("chain 'myChain'{ Filter -> Filter -> OtherFilter }"));
    }

    @Test
    public void non_equal_due_to_different_components() {
        assertThat(new Chain<>("a", new Filter()), is(not(new Chain<>("a", new Filter()))));
    }

    @Test
    public void non_equal_due_to_different_size_comopnents() {
        assertThat(new Chain<>("a", new Filter()), is(not(new Chain<Filter>("a"))));
    }

    @Test
    public void hashCode_equals() {
        assertThat(new Chain<>("a").hashCode(), is(new Chain<Filter>("a").hashCode()));
    }
}
