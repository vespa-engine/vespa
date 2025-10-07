// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.search.query.QueryTree;
import java.nio.ByteBuffer;

/**
 * The root node of a query tree. This is always present above the actual semantic root to ease query manipulation,
 * especially replacing the actual semantic root, but does not have any search semantics on its own.
 *
 * <p>To ease recursive manipulation of the query tree, this is a composite having one child, which is the actual root.
 * <ul>
 * <li>Setting the root item (at position 0, either directly or though the iterator of this, works as expected.
 * Setting at any other position is disallowed.
 * <li>Removing the root is allowed and causes this to be a null query.
 * <li>Adding an item is only allowed if this is currently a null query (having no root)
 * </ul>
 *
 * @author Arne Bergene Fossaa
 */
public class RootItem extends CompositeItem {

    public RootItem() {
        setRoot(new NullItem());
    }

    public RootItem(Item root) {
        setRoot(root);
    }

    @Override
    public void setIndexName(String index) {
        if (getRoot() != null)
            getRoot().setIndexName(index);
    }

    @Override
    public ItemType getItemType() {
        throw new RuntimeException("Packet type access attempted. A root item has no packet code. " +
                                   "This is probably a misbehaving searcher.");
    }

    @Override
    public String getName() { return "ROOT"; }

    @Override
    public int encode(ByteBuffer buffer) {
        if (getRoot() == null) return 0;
        return getRoot().encode(buffer);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf() {
        throw new UnsupportedOperationException("QueryTree itself should not be serialized, serialize its root");
    }

    /**
     * Convert this query tree to protobuf format.
     * @return a SearchProtocol.QueryTree protobuf message
     */
    public SearchProtocol.QueryTree toProtobufQueryTree() {
        var builder = SearchProtocol.QueryTree.newBuilder();
        if (getRoot() != null && !(getRoot() instanceof NullItem)) {
            builder.setRoot(getRoot().toProtobuf());
        }
        return builder.build();
    }

    // Let's not pollute toString() by adding "ROOT"
    @Override
    protected void appendHeadingString(StringBuilder sb) {
    }

    /** Returns the query root. This is null if this is a null query. */
    public Item getRoot() {
        if (getItemCount() == 0) return null;
        return getItem(0);
    }

    public final void setRoot(Item root) {
        if (root == this) throw new IllegalArgumentException("Cannot make a root point at itself");
        if (root == null) throw new IllegalArgumentException("Root must not be null, use NullItem instead.");
        if (root instanceof RootItem) throw new IllegalArgumentException("Do not use a new RootItem instance as a root.");
        if (root instanceof QueryTree) throw new IllegalArgumentException("Do not use a new QueryTree instance as a root.");
        if (this.getItemCount() == 0) // initializing
            super.addItem(root);
        else
            setItem(0, root); // replacing
    }

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof RootItem)) return false;
        return super.equals(o);
    }

    /** Returns a deep copy of this */
    @Override
    public RootItem clone() {
        return (RootItem) super.clone();
    }

    @Override
    public void addItem(Item item) {
        if (getItemCount() == 0)
            super.addItem(item);
        else
            throw new RuntimeException("Programming error: Cannot add multiple roots");
    }

    @Override
    public void addItem(int index, Item item) {
        if (getItemCount() == 0 && index == 0)
            super.addItem(index, item);
        else
            throw new RuntimeException("Programming error: Cannot add multiple roots, have '" + getRoot() + "'");
    }

    /** Returns true if this represents the null query */
    public boolean isEmpty() {
        return getRoot() == null || getRoot() instanceof NullItem || getItemCount() == 0;
    }

}
