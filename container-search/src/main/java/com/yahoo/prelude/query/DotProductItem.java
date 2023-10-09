// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Map;

/**
 * A weighted set query item to be evaluated as a sparse dot product.
 *
 * The resulting dot product will be available as a raw score in the rank framework.
 *
 * @author havardpe
 */
public class DotProductItem extends WeightedSetItem {

    public DotProductItem(String indexName) { super(indexName); }
    public DotProductItem(String indexName, Map<Object, Integer> map) { super(indexName, map); }

    @Override
    public ItemType getItemType() { return ItemType.DOTPRODUCT; }

}
