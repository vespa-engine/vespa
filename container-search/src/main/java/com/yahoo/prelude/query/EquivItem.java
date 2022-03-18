// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.protect.Validator;

import java.util.Collection;

/**
 * An Item where each child is an <i>alternative</i> which can be matched.
 * Produces the same recall as Or, but differs in that the relevance of a match
 * does not increase if more than one children is matched: With Equiv, matching one child perfectly is a perfect match.
 * <p>
 * This can only have Word, WordAlternatives, Exact, Int or Phrase children.
 *
 * @author havardpe
 */
public class EquivItem extends CompositeTaggableItem {

    /** Makes an EQUIV item with no children */
    public EquivItem() {}

    /**
     * Creates an EQUIV with the given item as child. The new EQUIV will take connectivity,
     * significance and weight from the given item.
     *
     * @param item will be modified and added as a child
     */
    public EquivItem(Item item) {
        addItem(item);

        // steal other item's connectivity:
        if (item.connectedItem != null) {
            setConnectivity(item.connectedItem, item.connectivity);
            item.connectedItem = null;
            item.connectivity = 0.0;
        }
        TaggableItem back = (TaggableItem)item.connectedBacklink;
        if (back != null) {
            back.setConnectivity(this, back.getConnectivity());
            item.connectedBacklink = null;
        }

        // steal other item's significance:
        if (item.explicitSignificance) {
            setSignificance(item.significance);
        }

        // steal other item's weight:
        setWeight(item.getWeight());

        // we have now stolen all the other item's unique id needs:
        item.setHasUniqueID(false);
    }

    /**
     * Creates an EQUIV with the given item and a set of alternate words as children.
     * The new EQUIV will take connectivity, significance and weight from the given item.
     *
     * @param item will be modified and added as a child
     * @param words set of words to create WordItems from
     */
    public EquivItem(Item item, Collection<String> words) {
        this(item);
        String idx = ((IndexedItem)item).getIndexName();
        for (String word : words) {
            WordItem witem = new WordItem(word, idx);
            addItem(witem);
        }
    }

    @Override
    public ItemType getItemType() {
        return ItemType.EQUIV;
    }

    @Override
    public String getName() {
        return "EQUIV";
    }

    @Override
    protected void adding(Item item) {
        super.adding(item);
        Validator.ensure("Could not add an item of type " + item.getItemType() +
                         ": Equiv can only have word, wordAlternatives, int, exact, or phrase as children",
                         acceptsChildrenOfType(item.getItemType()));
    }

    /** Returns true if this accepts child items of the given type */
    public static boolean acceptsChildrenOfType(ItemType itemType) {
        return itemType == ItemType.WORD ||
               itemType == ItemType.WORD_ALTERNATIVES ||
               itemType == ItemType.INT ||
               itemType == ItemType.EXACT ||
               itemType == ItemType.PHRASE;
    }

}
