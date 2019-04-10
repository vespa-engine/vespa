// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class QueryTreeTest {

    @Test
    public void testAddQueryItemWithRoot() {
        Assert.assertEquals("AND a b",
                            new QueryTree(new WordItem("a")).and(new WordItem("b")).toString());

        NotItem not = new NotItem();
        not.addNegativeItem(new WordItem("b"));
        assertEquals("+a -b",
                     new QueryTree(new WordItem("a")).and(not).toString());
     }

}
