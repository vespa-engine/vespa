// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


import java.nio.ByteBuffer;


/**
 * A place holder for null queries to make searchers easier to write.
 *
 * @author Steinar Knutsen
 */
public class NullItem extends Item {

    public NullItem() {}

    /** Does nothing */
    public void setIndexName(String index) {}

    public int encode(ByteBuffer buffer) {
        throw new RuntimeException(
                "A NullItem was attempted encoded. "
                        + "This is probably a misbehaving " + "searcher.");
    }

    public ItemType getItemType() {
        throw new RuntimeException(
                "Packet code access attempted. "
                        + "A NullItem has no packet code. "
                        + "This is probably a misbehaving " + "searcher.");
    }

    public void appendBodyString(StringBuilder buffer) {
        // No body for this Item
        return;
    }

    public void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
    }

    public String getName() {
        return "NULL";
    }

    @Override
    public int getTermCount() { return 0; }

}
