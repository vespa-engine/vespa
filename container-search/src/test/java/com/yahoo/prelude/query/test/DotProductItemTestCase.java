// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import com.yahoo.prelude.query.*;

/**
 * @author havardpe
 */
public class DotProductItemTestCase {

    @Test
    public void testDotProductItem() {
        DotProductItem item = new DotProductItem("index_name");
        assertEquals("index_name", item.getIndexName());
        assertEquals(Item.ItemType.DOTPRODUCT, item.getItemType());
    }

    @Test
    public void testDotProductClone() {
        DotProductItem dpOrig = new DotProductItem("myDP");
        dpOrig.addToken("first",11);
        dpOrig.getTokens();
        DotProductItem dpClone = (DotProductItem) dpOrig.clone();
        dpClone.addToken("second", 22);
        assertEquals(2, dpClone.getNumTokens());
    }

}
