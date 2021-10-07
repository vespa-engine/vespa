// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.protect.Validator;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A composite item where the first item is positive and the following
 * items are negative items which should be excluded from the result.
 *
 * @author bratseth
 */
// TODO: Handle nulls by creating nullItem or checking in encode/toString
public class NotItem extends CompositeItem {

    public ItemType getItemType() {
        return ItemType.NOT;
    }

    public String getName() {
        return "NOT";
    }

    /**
     * Adds an item. The first item is the positive
     * the rest is negative
     */
    public void addItem(Item item) {
        super.addItem(item);
    }

    /**
     * Adds a negative item. Like addItem but skips the first position
     * (position 0) if it is not already set.
     */
    public void addNegativeItem(Item negative) {
        if (getItemCount() == 0) {
            insertNullFirstItem();
        }
        addItem(negative);
    }

    /** Returns the negative items of this: All child items except the first */
    public List<Item> negativeItems() { return items().subList(1, getItemCount()); }

    /**
     * Returns the positive item (the first subitem),
     * or null if no positive items has been added
     */
    public Item getPositiveItem() {
        if (getItemCount() == 0) {
            return null;
        }
        return getItem(0);
    }

    /**
     * Sets the positive item (the first item)
     *
     * @return the old positive item, or null if there was no items
     */
    public Item setPositiveItem(Item item) {
        Validator.ensureNotNull("Positive item of " + this, item);
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
        if (getPositiveItem() == null) {
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
            insertNullFirstItem();
        }
        return removed;
    }

    public Item removeItem(int index) {
        Item removed = super.removeItem(index);

        if (index == 0) { // Don't make the first negative the positive
            insertNullFirstItem();
        }
        return removed;
    }

    /** Not items uses a empty heading instead of "NOT " */
    protected void appendHeadingString(StringBuilder buffer) {}

    /**
     * Overridden to tolerate nulls and to append "+"
     * to the first item and "-" to the rest
     */
    protected void appendBodyString(StringBuilder buffer) {
        boolean isFirstItem = true;

        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item item = i.next();

            if (isFirstItem) {
                buffer.append("+");
            } else {
                buffer.append(" -");
            }
            if (item == null) {
                buffer.append("(null)");
            } else {
                buffer.append(item.toString());
            }
            isFirstItem = false;
        }
    }

    /** Returns the number of actual *positive* terms in this */
    @Override
    public int getTermCount() {
        Item positive = getPositiveItem();
        return positive == null ? 0 : positive.getTermCount();
    }

}
