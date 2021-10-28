// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;

/**
 * An item which cannot provide its own index (field) name, but will always query the index
 * specified by the parent item it is added to.
 * It's more efficient to use pure items where possible instead of
 * {@link TermItem} children ({@link WordItem}, {@link IntItem})
 * who each carry their own index name.
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

}
