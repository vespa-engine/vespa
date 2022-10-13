// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.prelude.query.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

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
 * <p>This is also the home of accessor methods which eases querying into and manipulation of the query tree.</p>
 *
 * @author Arne Bergene Fossaa
 */
public class QueryTree extends CompositeItem {

    public QueryTree(Item root) {
        setRoot(root);
    }

    public void setIndexName(String index) {
        if (getRoot() != null)
            getRoot().setIndexName(index);
    }

    public ItemType getItemType() {
        throw new RuntimeException("Packet type access attempted. A query tree has no packet code. " + 
                                   "This is probably a misbehaving searcher.");
    }

    public String getName() { return "ROOT"; }

    public int encode(ByteBuffer buffer) {
        if (getRoot() == null) return 0;
        return getRoot().encode(buffer);
    }

    // Let's not pollute toString() by adding "ROOT"
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
        if (root instanceof QueryTree) throw new IllegalArgumentException("Do not use a new QueryTree instance as a root.");
        if (this.getItemCount() == 0) // initializing
            super.addItem(root);
        else
            setItem(0, root); // replacing
    }

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof QueryTree)) return false;
        return super.equals(o);
    }

    /** Returns a deep copy of this */
    @Override
    public QueryTree clone() {
        QueryTree clone = (QueryTree) super.clone();
        fixClonedConnectivityReferences(clone);
        return clone;
    }

    private void fixClonedConnectivityReferences(QueryTree clone) {
        // TODO!
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
        return getRoot() instanceof NullItem || getItemCount() == 0;
    }

    // -------------- Facade

    /**
     * Modifies this query to become the current query RANK with the given item.
     *
     * @return the resulting root item in this
     */
    public Item withRank(Item item) {
        var result = new RankItem();
        result.addItem(getRoot());
        result.addItem(item);
        setRoot(result);
        return result;
    }

    /**
     * Modifies this query to become the current query AND the given item.
     *
     * @return the resulting root item in this
     */
    public Item and(Item item) {
        Item result = and(getRoot(), item);
        setRoot(result);
        return result;
    }

    private Item and(Item a, Item b) {
        if (a == null || a instanceof NullItem) {
            return b;
        }
        else if (b == null || b instanceof NullItem) {
            return a;
        }
        else if (a instanceof NotItem notItemA && b instanceof NotItem notItemB) {
            NotItem combined = new NotItem();
            combined.addPositiveItem(and(notItemA.getPositiveItem(), notItemB.getPositiveItem()));
            notItemA.negativeItems().forEach(item -> combined.addNegativeItem(item));
            notItemB.negativeItems().forEach(item -> combined.addNegativeItem(item));
            return combined;
        }
        else if (a instanceof NotItem notItem){
            notItem.addPositiveItem(b);
            return a;
        }
        else if (b instanceof NotItem notItem){
            notItem.addPositiveItem(a);
            return notItem;
        }
        else if (a instanceof AndItem) {
            ((AndItem)a).addItem(b);
            return a;
        }
        else {
            AndItem andItem = new AndItem();
            andItem.addItem(a);
            andItem.addItem(b);
            return andItem;
        }
    }

    /** Returns a flattened list of all positive query terms under the given item */
    public static List<IndexedItem> getPositiveTerms(Item item) {
        List<IndexedItem> items = new ArrayList<>();
        getPositiveTerms(item,items);
        return items;
    }

    private static void getPositiveTerms(Item item, List<IndexedItem> terms) {
        if (item instanceof NotItem notItem) {
            getPositiveTerms(notItem.getPositiveItem(), terms);
        } else if (item instanceof PhraseItem phraseItem) {
            terms.add(phraseItem);
        } else if (item instanceof CompositeItem compositeItem) {
            for (Iterator<Item> i = compositeItem.getItemIterator(); i.hasNext();) {
                getPositiveTerms(i.next(), terms);
            }
        } else if (item instanceof TermItem termItem) {
            terms.add(termItem);
        }
    }

    /**
     * Returns the total number of items in this query tree.
     */
    public int treeSize() {
        if (isEmpty()) return 0;
        return countItemsRecursively(getItemIterator().next());
    }

    private int countItemsRecursively(Item item) {
        int children = 0;
        if (item instanceof CompositeItem composite) {
            for (ListIterator<Item> i = composite.getItemIterator(); i.hasNext(); ) {
                children += countItemsRecursively(i.next());
            }
        }
        return children + 1;
    }

}
