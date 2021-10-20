// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;

/**
 * A query item which never matches. This is sometimes a useful output of query rewriting.
 *
 * @author bratseth
 */
public class FalseItem extends Item {

    @Override
    public void setIndexName(String index) { }

    @Override
    public ItemType getItemType() {
        return ItemType.FALSE;
    }

    @Override
    public String getName() { return "FALSE"; }

    /** Override to only return "FALSE" rather than "FALSE " */
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
