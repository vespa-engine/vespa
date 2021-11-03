// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;

/**
 * A word item which only consists of a value and weight, and gets other properties
 * such as the index to query from ther parent item.
 *
 * It's more efficient to use pure items where possible instead of
 * {@link TermItem} children ({@link WordItem}, {@link IntItem})
 * which may carry many auxiliary properties.
 *
 * @author baldersheim
 */
public abstract class PureWeightedItem extends Item {

    public PureWeightedItem(int weight) {
        setWeight(weight);
    }

    /** Ignored. */
    @Override
    public void setIndexName(String index) {
        // No index
    }

    @Override
    public String getName() {
        return getItemType().name();
    }

    @Override
    public int encode(ByteBuffer buffer) {
        encodeThis(buffer);
        return 1;
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append(':').append(getWeight());
    }

    @Override
    public void disclose(Discloser discloser) {
        discloser.addProperty("weight", getWeight());
    }

}
