// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A composite item where the first item is positive and the following
 * items are negative items where matches should exclude the document should from the result.
 * The default positive item, if only negatives are added, is TrueItem: Meaning that all documents are matched
 * except those matching the negative terms added.
 *
 * @author bratseth
 */
public class NotItem extends CompositeItem {

    @Override
    public ItemType getItemType() {
        return ItemType.NOT;
    }

    @Override
    public String getName() {
        return "NOT";
    }

    /** Adds an item. The first item is the positive, the rest are negative */
    @Override
    public void addItem(Item item) {
        super.addItem(item);
    }

    /**
     * Adds a negative item. Like addItem but skips the first position
     * (position 0) if it is not already set.
     */
    public void addNegativeItem(Item negative) {
        if (getItemCount() == 0)
            insertTrueFirstItem();
        addItem(negative);
    }

    /** Returns the negative items of this: All child items except the first */
    public List<Item> negativeItems() { return items().subList(1, getItemCount()); }

    /** Returns the positive item (the first subitem), or TrueItem if no positive items has been added. */
    public Item getPositiveItem() {
        if (getItemCount() == 0)
            return new TrueItem();
        return getItem(0);
    }

    /**
     * Sets the positive item (the first item)
     *
     * @return the old positive item, or TrueItem if there was none
     */
    public Item setPositiveItem(Item item) {
        Objects.requireNonNull(item, () -> "Positive item of " + this);
        if (getItemCount() == 0) {
            addItem(item);
            return null;
        } else {
            return setItem(0, item);
        }
    }

    /**
     * Convenience method for adding a positive item.
     * If a positive item is already present
     * the positive item becomes an AndItem with the items added
     */
    public void addPositiveItem(Item item) {
        if (getPositiveItem() instanceof TrueItem) {
            setPositiveItem(item);
        } else if (getPositiveItem() instanceof AndItem) {
            ((AndItem) getPositiveItem()).addItem(item);
        } else {
            AndItem positives = new AndItem();

            positives.addItem(getPositiveItem());
            positives.addItem(item);
            setPositiveItem(positives);
        }
    }

    public boolean removeItem(Item item) {
        int removedIndex = getItemIndex(item);
        boolean removed = super.removeItem(item);

        if (removed && removedIndex == 0) {
            insertTrueFirstItem();
        }
        return removed;
    }

    public Item removeItem(int index) {
        Item removed = super.removeItem(index);

        if (index == 0) { // Don't make the first negative the positive
            insertTrueFirstItem();
        }
        return removed;
    }

    private void insertTrueFirstItem() {
        addItem(0, new TrueItem());
    }

    /** Not items uses a empty heading instead of "NOT " */
    protected void appendHeadingString(StringBuilder buffer) {}

    /**
     * Overridden to skip the positive TrueItem and (otherwise) append "+"
     * to the first item and "-" to the rest
     */
    @Override
    protected void appendBodyString(StringBuilder buffer) {
        if (items().isEmpty()) return;
        if (items().size() == 1) {
            buffer.append(items().get(0));
            return;
        }
        for (int i = 0; i < items().size(); i++) {
            if (i == 0 && items().get(i) instanceof TrueItem) continue; // skip positive true

            buffer.append(i == 0 ? "+" : "-").append(items().get(i));
            if ( i < items().size() - 1)
                buffer.append(" ");
        }
    }

    /** Returns the number of actual *positive* terms in this */
    @Override
    public int getTermCount() {
        Item positive = getPositiveItem();
        return positive == null ? 0 : positive.getTermCount();
    }

}
