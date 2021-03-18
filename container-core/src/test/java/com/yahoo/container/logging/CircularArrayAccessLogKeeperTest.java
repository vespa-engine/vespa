// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class CircularArrayAccessLogKeeperTest {
    private CircularArrayAccessLogKeeper circularArrayAccessLogKeeper = new CircularArrayAccessLogKeeper();

    @Test
    public void testSizeIsCroppedCorrectly() {
        for (int i = 0; i < CircularArrayAccessLogKeeper.SIZE - 1; i++) {
            circularArrayAccessLogKeeper.addUri(String.valueOf(i));
        }
        assertThat(circularArrayAccessLogKeeper.getUris().size(), is(CircularArrayAccessLogKeeper.SIZE -1));
        circularArrayAccessLogKeeper.addUri("foo");
        assertThat(circularArrayAccessLogKeeper.getUris().size(), is(CircularArrayAccessLogKeeper.SIZE));
        circularArrayAccessLogKeeper.addUri("bar");
        assertThat(circularArrayAccessLogKeeper.getUris().size(), is(CircularArrayAccessLogKeeper.SIZE));
        assertThat(circularArrayAccessLogKeeper.getUris(), hasItems("1", "2", "3", "foo", "bar"));
        assertThat(circularArrayAccessLogKeeper.getUris(), not(hasItem("0")));
    }

    @Test
    public void testEmpty() {
        assertThat(circularArrayAccessLogKeeper.getUris().size(), is(0));
    }

    @Test
    public void testSomeItems() {
        circularArrayAccessLogKeeper.addUri("a");
        circularArrayAccessLogKeeper.addUri("b");
        circularArrayAccessLogKeeper.addUri("b");
        assertThat(circularArrayAccessLogKeeper.getUris(), contains("a", "b", "b"));
    }
}
