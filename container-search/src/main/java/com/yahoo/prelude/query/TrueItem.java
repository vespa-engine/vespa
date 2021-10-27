// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;

/**
 * A query item which matches everything.
 *
 * @author arnej
 */
public class TrueItem extends Item {

    @Override
    public void setIndexName(String index) {
        throw new IllegalArgumentException("TrueItem should not have an index name");
    }

    @Override
    public ItemType getItemType() { return ItemType.TRUE; }

    @Override
    public String getName() { return "TRUE"; }

    /** Override to only return "TRUE" rather than "TRUE " */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
    }

    @Override
    public int encode(ByteBuffer buffer) {
        super.encodeThis(buffer);
        return 1;
    }

    @Override
    public int getTermCount() { return 0; }

    @Override
    protected void appendBodyString(StringBuilder buffer) { }
}
