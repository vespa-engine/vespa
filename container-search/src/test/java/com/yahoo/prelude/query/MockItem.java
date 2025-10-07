// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import java.nio.ByteBuffer;

/**
 * A mock query item for testing purposes.
 *
 * @author Tony Vaagenes
 */
public class MockItem extends Item {

    private final String name;

    public MockItem(String name) { this.name = name; }
    @Override public void setIndexName(String index) { }
    @Override public ItemType getItemType() { return null; }
    @Override public String getName() { return name; }
    @Override public int encode(ByteBuffer buffer) { return 0; }
    @Override public int getTermCount() { return 0; }
    @Override protected void appendBodyString(StringBuilder buffer) { }
    @Override protected SearchProtocol.QueryTreeItem toProtobuf() {
        throw new UnsupportedOperationException("MockItem does not support protobuf serialization");
    }

}
