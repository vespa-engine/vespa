// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * Common implementation for Item classes implementing the TaggableItem interface.
 * Note that this file exist in 3 copies that should be kept in sync:
 *
 * CompositeTaggableItem.java
 * SimpleTaggableItem.java
 * TaggableSegmentItem.java
 *
 * These should only have trivial differences.
 * (multiple inheritance or mixins would have been nice).
 *
 * @author arnej27959
 */
public abstract class TaggableSegmentItem extends SegmentItem implements TaggableItem {

    protected TaggableSegmentItem(String rawWord, String current, boolean isFromQuery, boolean stemmed, Substring origin) {
        super(rawWord, current, isFromQuery, stemmed, origin);
    }

    @Override
    public int getUniqueID() {
        return uniqueID;
    }

    @Override
    public void setUniqueID(int id) {
        setHasUniqueID(true);
        uniqueID = id;
    }

    /** See {@link TaggableItem#setConnectivity} */
    @Override
    public void setConnectivity(Item item, double connectivity) {
        if (!(item instanceof TaggableItem)) {
            throw new IllegalArgumentException("setConnectivity item must be taggable, was: "
                                               + item.getClass() + " [" + item + "]");
        }
        setHasUniqueID(true);
        item.setHasUniqueID(true);
        if (connectedItem != null) {
            // untangle old connectivity
            connectedItem.connectedBacklink = null;
        }
        this.connectivity = connectivity;
        connectedItem = item;
        connectedItem.connectedBacklink = this;
    }

    @Override
    public Item getConnectedItem() {
        return connectedItem;
    }

    @Override
    public double getConnectivity() {
        return connectivity;
    }

    @Override
    public void setSignificance(double significance) {
        setHasUniqueID(true);
        setExplicitSignificance(true);
        this.significance = significance;
    }

    @Override
    public void setExplicitSignificance(boolean explicitSignificance) {
        this.explicitSignificance = explicitSignificance;
    }

    @Override
    public boolean hasExplicitSignificance() {
        return explicitSignificance;
    }

    @Override
    public double getSignificance() {
        return significance;
    }

    //Change access privilege from protected to public.
    @Override
    public boolean hasUniqueID() {
        return super.hasUniqueID();
    }

}
