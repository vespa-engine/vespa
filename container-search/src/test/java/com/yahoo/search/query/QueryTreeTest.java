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

     @Test
     public void addNotToNot() {
         NotItem not1 = new NotItem();
         not1.addPositiveItem(new WordItem("p1"));
         not1.addNegativeItem(new WordItem("n1.1"));
         not1.addNegativeItem(new WordItem("n1.2"));

         NotItem not2 = new NotItem();
         not2.addPositiveItem(new WordItem("p2"));
         not2.addNegativeItem(new WordItem("n2.1"));
         not2.addNegativeItem(new WordItem("n2.2"));

         QueryTree tree = new QueryTree(not1);
         tree.and(not2);

         assertEquals("+(AND p1 p2) -n1.1 -n1.2 -n2.1 -n2.2", tree.toString());
     }

}
