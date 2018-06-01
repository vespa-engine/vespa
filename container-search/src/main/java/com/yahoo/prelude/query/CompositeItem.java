// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.protect.Validator;
import com.yahoo.search.query.QueryTree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;


/**
 * Superclass of expressions which contains a collection of sub-expressions
 *
 * @author bratseth
 */
public abstract class CompositeItem extends Item {

    private List<Item> subitems = new java.util.ArrayList<>(4);

    /** Sets the index name of all subitems of this */
    public void setIndexName(String index) {
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item item = i.next();

            item.setIndexName(index);
        }
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        for (Item item : subitems)
            discloser.addChild(item);
    }

    public void ensureNotInSubtree(CompositeItem item) {
        for (Iterator<Item> i = item.getItemIterator(); i.hasNext();) {
            Item possibleCycle = i.next();

            if (this == possibleCycle) {
                throw new QueryException("Tried to create a cycle in a tree.");
            } else if (possibleCycle instanceof CompositeItem) {
                ensureNotInSubtree((CompositeItem) possibleCycle);
            }
        }
    }

    public void addItem(Item item) {
        adding(item);
        subitems.add(item);
    }

    protected void adding(Item item) {
        Validator.ensureNotNull("Composite item", item);
        Validator.ensure("Attempted to add a composite to itself", item != this);
        if (item instanceof CompositeItem) {
            ensureNotInSubtree((CompositeItem) item);
        }
        item.setParent(this);
    }

    /**
     * Inserts the item at a position and increases the index of existing items
     * starting on this position by one
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void addItem(int index, Item item) {
        if (index > subitems.size() || index < 0) {
            throw new IndexOutOfBoundsException(
                    "Could not add a subitem at position " + index + " to " + this);
        }
        adding(item);
        subitems.add(index, item);
    }

    /** For NOT items, which may wish to insert nulls */
    void insertNullFirstItem() {
        subitems.add(0, null);
    }

    /**
     * Returns a subitem
     *
     * @param index the (0-base) index of the item to return
     * @throws IndexOutOfBoundsException if there is no subitem at index
     */
    public Item getItem(int index) {
        return subitems.get(index);
    }

    /**
     * Replaces the item at the given index
     *
     * @param  index the (0-base) index of the item to replace
     * @param  item the new item
     * @return the old item at this position. The parent of the old item is <i>not</i> cleared
     * @throws IndexOutOfBoundsException if there is no item at this index
     */
    public Item setItem(int index, Item item) {
        if (index >= subitems.size() || index < 0)
            throw new IndexOutOfBoundsException("Could not add a subitem at position " + index + " to " + this);

        adding(item);
        Item old = subitems.set(index, item);
        if (old!=item)
            removing(old);
        return old;
    }

    /**
     * Returns the index of a subitem
     *
     * @param  item The child item to find the index of
     * @return the 0-base index of the child or -1 if there is no such child
     */
    public int getItemIndex(Item item) {
        return subitems.indexOf(item);
    }

    /**
     * Removes the item at the given index
     *
     * @param  index the index of the item to remove
     * @return the removed item
     * @throws IndexOutOfBoundsException if there is no item at the given index
     */
    public Item removeItem(int index) {
        Item item = subitems.remove(index);

        removing(item);
        return item;
    }

    /** Always call on every remove */
    private void removing(Item item) {
        if (item == null) {
            return;
        }
        if (item.getParent() == this) { // Otherwise, this belongs to somebody else now (somebody are doing addField, removeField)
            item.setParent(null);
        }
    }

    /**
     * Removes the given item. Does nothing if the item is not present.
     *
     * @param  item the item to remove
     * @return whether the item was removed
     */
    public boolean removeItem(Item item) {
        boolean removed = subitems.remove(item);

        if (removed) {
            removing(item);
        }
        return removed;
    }

    /** Returns the number of direct ancestors of this item */
    public int getItemCount() {
        return subitems.size();
    }

    /** Returns a modifiable list iterator */
    public ListIterator<Item> getItemIterator() {
        return new ListIteratorWrapper(this);
    }

    public int encode(ByteBuffer buffer) {
        encodeThis(buffer);
        int itemCount = 1;

        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item subitem = i.next();

            itemCount += subitem.encode(buffer);
        }
        return itemCount;
    }

    /**
     * Encodes just this item, not it's usual subitems, to the given buffer.
     */
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        IntegerCompressor.putCompressedPositiveNumber(encodingArity(), buffer);
    }

    protected int encodingArity() {
        return subitems.size();
    }

    protected void appendBodyString(StringBuilder buffer) {
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item item = i.next();

            buffer.append(item.toString());
            if (i.hasNext()) {
                buffer.append(" ");
            }
        }
    }

    /** Composite items should be parenthized when not on the top level */
    protected boolean shouldParenthize() {
        return getParent()!= null && ! (getParent() instanceof QueryTree);
    }

    /** Returns a deep copy of this item */
    public CompositeItem clone() {
        CompositeItem copy = (CompositeItem) super.clone();

        copy.subitems = new java.util.ArrayList<>();
        for (Item subItem : subitems) {
            copy.subitems.add(subItem.clone());
        }
        fixConnexity(copy);
        return copy;
    }

    private void fixConnexity(CompositeItem copy) {
        List<Item> flatland = new ArrayList<>();
        List<Item> flatCopy = new ArrayList<>();
        taggingFlatten(this, flatland);
        taggingFlatten(copy, flatCopy);
        int barrier = flatland.size();
        for (int i = 0; i < barrier; ++i) {
            Item orig = flatland.get(i);
            int connectedTo = find(orig.connectedItem, flatland);
            if (connectedTo >= 0) {
                TaggableItem tagged = (TaggableItem) flatCopy.get(i);
                tagged.setConnectivity(flatCopy.get(connectedTo), orig.connectivity);
            }
        }
    }

    private void taggingFlatten(Item tree, List<Item> container) {
        if (tree.hasUniqueID()) {
            container.add(tree);
        } else if (tree instanceof CompositeItem) {
            CompositeItem asComposite = (CompositeItem) tree;
            for (Iterator<Item> i = asComposite.getItemIterator(); i.hasNext();) {
                taggingFlatten(i.next(), container);
            }
        }
    }

    private int find(Item needle, List<Item> haystack) {
        if (needle == null) {
            return -1;
        }
        int barrier = haystack.size();
        for (int i = 0; i < barrier; ++i) {
            if (haystack.get(i) == needle) {
                return i;
            }
        }
        return -1;
    }

    public int hashCode() {
        int code = getName().hashCode() + subitems.size() * 17;

        for (int i = 0; i < subitems.size() && i <= 5; i++) {
            code += subitems.get(i).hashCode();
        }
        return code;
    }

    /**
     * Returns whether this item is of the same class and
     * contains the same state as the given item
     */
    public boolean equals(Object object) {
        if (!super.equals(object)) {
            return false;
        }

        CompositeItem other = (CompositeItem) object; // Ensured by superclass

        if (!this.subitems.equals(other.subitems)) {
            return false;
        }

        return true;
    }

    /** Make composite immutable if this is supported. */
    public void lock() {}

    /** Whether this composite is in a mutable state. */
    public boolean isLocked() {
        return false;
    }

    /** Handles mutator calls correctly */
    private static class ListIteratorWrapper implements ListIterator<Item> {

        private CompositeItem owner;

        private ListIterator<Item> wrapped;

        private Item current = null;

        public ListIteratorWrapper(CompositeItem owner) {
            this.owner = owner;
            wrapped = owner.subitems.listIterator();
        }

        public boolean hasNext() {
            return wrapped.hasNext();
        }

        public Item next() {
            current = wrapped.next();
            return current;
        }

        public boolean hasPrevious() {
            return wrapped.hasPrevious();
        }

        public Item previous() {
            Item current = wrapped.previous();

            return current;
        }

        public int nextIndex() {
            return wrapped.nextIndex();
        }

        public int previousIndex() {
            return wrapped.previousIndex();
        }

        public void remove() {
            owner.removing(current);
            wrapped.remove();
        }

        public void set(Item o) {
            Item newItem = o;

            owner.removing(current);
            owner.adding(newItem);
            current = newItem;
            wrapped.set(newItem);
        }

        public void add(Item o) {
            Item newItem = o;

            owner.adding(newItem);
            // TODO: Change current here? Check javadoc
            wrapped.add(o);
        }

    }

    @Override
    public int getTermCount() {
        int terms = 0;
        for (Item item : subitems) {
            terms += item.getTermCount();
        }
        return terms;
    }

}
