// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;

/**
 * A placeholder for null queries to make searchers easier to write.
 *
 * @author Steinar Knutsen
 */
public class NullItem extends Item {

    public NullItem() {}

    /** Does nothing */
    @Override
    public void setIndexName(String index) {}

    @Override
    public int encode(ByteBuffer buffer) {
        throw new IllegalStateException("A NullItem was attempted encoded. This is probably a misbehaving searcher");
    }

    @Override
    public ItemType getItemType() {
        throw new IllegalStateException("Packet code access attempted. A NullItem has no packet code. " +
                                        "This is probably a misbehaving searcher.");
    }

    @Override
    public void appendBodyString(StringBuilder buffer) {}

    public void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
    }

    @Override
    public String getName() {
        return "NULL";
    }

    @Override
    public int getTermCount() { return 0; }

}
