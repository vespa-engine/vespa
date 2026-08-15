// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

/**
 * Behaves like the rank operator with a single operand: it does not change which hits
 * are returned, but carries a score which the back-end can use to rank the documents
 * matching the operand.
 *
 * The score is addressed from the ranking framework by the label of this item, e.g. by
 * the itemRawScore(label) rank feature.
 *
 * @author arnej
 */
public class LabelWrapperItem extends CompositeTaggableItem {

    private double labelScore;

    public LabelWrapperItem(String label, double labelScore) {
        setLabel(Objects.requireNonNull(label, "label cannot be null"));
        this.labelScore = labelScore;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.LABEL_WRAPPER;
    }

    @Override
    public String getName() {
        return "LABEL_WRAPPER";
    }

    /** Returns the score of this label. */
    public double getLabelScore() { return labelScore; }

    public void setLabelScore(double labelScore) { this.labelScore = labelScore; }

    /** This is never reducible: dropping it would lose the label/score marker. */
    @Override
    public Optional<Item> extractSingleChild() {
        return Optional.empty();
    }

    @Override
    public void addItem(Item item) {
        ensureRoomForOneMore();
        super.addItem(item);
    }

    @Override
    public void addItem(int index, Item item) {
        ensureRoomForOneMore();
        super.addItem(index, item);
    }

    private void ensureRoomForOneMore() {
        if (getItemCount() > 0)
            throw new IllegalArgumentException(getName() + " can only have a single child");
    }

    private Item child() {
        if (getItemCount() != 1)
            throw new IllegalStateException(getName() + " must have exactly one child, but has " + getItemCount());
        return getItem(0);
    }

    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context);
        buffer.putDouble(labelScore);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemLabelWrapper.newBuilder();
        builder.setUniqueId(getUniqueID());
        builder.setScore(labelScore);
        builder.setChild(child().toProtobuf(context));
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemLabelWrapper(builder.build())
                .build();
    }

    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        buffer.append("(").append(getLabel()).append(",").append(labelScore).append(") ");
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("labelScore", labelScore);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), labelScore); }

    /** Returns whether this item is of the same class and contains the same state as the given item. */
    @Override
    public boolean equals(Object object) {
        if ( ! super.equals(object)) return false;
        LabelWrapperItem other = (LabelWrapperItem) object; // Ensured by superclass
        if (Double.compare(this.labelScore, other.labelScore) != 0) return false;
        return true;
    }

}
