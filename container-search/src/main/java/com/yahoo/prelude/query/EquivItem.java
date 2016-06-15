// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.protect.Validator;

import java.util.Collection;

/**
 * An Item where each child is an <i>alternative</i> which can be matched.
 * Produces the same recall as Or, but differs in that the relevance of a match
 * does not increase if more than one children is matched: With Equiv, matching one child perfectly is a perfect match.
 * <p>
 * This can only have Word, Int or Phrase children.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">havardpe</a>
 */
public class EquivItem extends CompositeTaggableItem {

    public ItemType getItemType() {
        return ItemType.EQUIV;
    }

    public String getName() {
        return "EQUIV";
    }

    @Override
    protected void adding(Item item) {
        super.adding(item);
        Validator.ensure("Equiv can only have word/int/phrase as children",
                         item.getItemType() == ItemType.WORD ||
                         item.getItemType() == ItemType.INT ||
                         item.getItemType() == ItemType.PHRASE);
    }

    /** make an EQUIV item with no children */
    public EquivItem() {}

    /**
     * create an EQUIV with the given item as child.
     * The new EQUIV will take connectivity,
     * significance and weight from the given item.
     *
     * @param item Will be modified and added as a child.
     **/
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

        // we have now stolen all of the other item's unique id needs:
        item.setHasUniqueID(false);
    }

    /**
     * create an EQUIV with the given item and a set
     * of alternate words as children.
     * The new EQUIV will take connectivity,
     * significance and weight from the given item.
     *
     * @param item Will be modified and added as a child.
     * @param words Set of words to create WordItems from.
     **/
    public EquivItem(Item item, Collection<String> words) {
        this(item);
        String idx = ((IndexedItem)item).getIndexName();
        for (String word : words) {
            WordItem witem = new WordItem(word, idx);
            addItem(witem);
        }
    }
}
