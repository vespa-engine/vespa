// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

/**
 * Note: Most of the functionality is tested implicitly by other tests
 *
 * @author hmusum
 */
public class TimingValuesTest {

    @Test
    public void basic() {
        TimingValues tv = new TimingValues();
        TimingValues tv2 = new TimingValues(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1);
        assertThat(tv.getRandom(), is(not(tv2.getRandom())));
        TimingValues copy = new TimingValues(tv2);
        assertThat(copy.toString(), is(tv2.toString()));  // No equals method, just using toString to compare
    }
}
