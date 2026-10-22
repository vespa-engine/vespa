// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Represents a query item matching the documents with the highest dot product between a sparse
 * document tensor and a sparse query tensor, both having exactly one mapped dimension.
 * The query tensor is referenced by the name of a tensor passed as a query rank feature;
 * specifying "myvector" as the name means the query must set "ranking.features.query(myvector)".
 * The field name (AKA the index name) given must be an attribute with the exact same tensor type.
 *
 * Like {@link WandItem}, a heap of the best targetHits scores is used to gradually raise the
 * score threshold a document must exceed to become a hit.
 *
 * @author arnej
 */
public class SparseTensorWandItem extends SimpleTaggableItem {

    /** The default targetHits used if none is specified: 100 */
    private static final int defaultTargetHits = 100;

    private String field;
    private final String queryTensorName;
    private Integer targetHits = null;
    private Integer totalTargetHits = null;
    private double scoreThreshold = 0;

    public SparseTensorWandItem(String fieldName, String queryTensorName) {
        this.field = Objects.requireNonNull(fieldName);
        this.queryTensorName = Objects.requireNonNull(queryTensorName);
    }

    /** Returns the name of the index (field) to be searched */
    public String getIndexName() { return field; }

    /** Returns the name of the query tensor */
    public String getQueryTensorName() { return queryTensorName; }

    /** Returns the number of hits to produce per node, or null if not set. */
    public Integer getTargetHits() { return targetHits; }

    /** Returns the total number of hits to produce across all nodes, or null if not set. */
    public Integer getTotalTargetHits() { return totalTargetHits; }

    /** Returns the initial score threshold a document must exceed to become a hit. */
    public double getScoreThreshold() { return scoreThreshold; }

    /** Sets the number of hits to produce per node. */
    public void setTargetHits(Integer target) { this.targetHits = target; }

    /** Sets the total number of hits to produce across all nodes. */
    public void setTotalTargetHits(Integer total) { this.totalTargetHits = total; }

    /** Sets the initial score threshold a document must exceed to become a hit. */
    public void setScoreThreshold(double scoreThreshold) { this.scoreThreshold = scoreThreshold; }

    @Override
    public void setIndexName(String index) { this.field = index; }

    @Override
    public ItemType getItemType() { return ItemType.SPARSE_TENSOR_WAND; }

    @Override
    public String getName() { return "SPARSE_TENSOR_WAND"; }

    @Override
    public int getTermCount() { return 1; }

    @Override
    public int encode(ByteBuffer buffer, SerializationContext context) {
        throw new UnsupportedOperationException(getName() + " can only be serialized as protobuf");
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append("{field=").append(field);
        buffer.append(",queryTensorName=").append(queryTensorName);
        if (targetHits != null)
            buffer.append(",targetHits=").append(targetHits);
        if (totalTargetHits != null)
            buffer.append(",totalTargetHits=").append(totalTargetHits);
        if (scoreThreshold != 0)
            buffer.append(",scoreThreshold=").append(scoreThreshold);
        buffer.append("}");
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("field", field);
        discloser.addProperty("queryTensorName", queryTensorName);
        if (targetHits != null)
            discloser.addProperty("targetHits", targetHits);
        if (totalTargetHits != null)
            discloser.addProperty("totalTargetHits", totalTargetHits);
        if (scoreThreshold != 0)
            discloser.addProperty("scoreThreshold", scoreThreshold);
    }

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        var other = (SparseTensorWandItem)o;
        if ( ! Objects.equals(this.targetHits, other.targetHits)) return false;
        if ( ! Objects.equals(this.totalTargetHits, other.totalTargetHits)) return false;
        if (this.scoreThreshold != other.scoreThreshold) return false;
        if ( ! this.field.equals(other.field)) return false;
        if ( ! this.queryTensorName.equals(other.queryTensorName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetHits, totalTargetHits, scoreThreshold, field, queryTensorName);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemSparseTensorWand.newBuilder();
        builder.setProperties(ToProtobuf.buildTermProperties(this, getIndexName()));
        builder.setQueryTensorName(queryTensorName);
        builder.setTargetNumHits(resolveTargetHits(context));
        builder.setScoreThreshold(scoreThreshold);
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemSparseTensorWand(builder.build())
                .build();
    }

    private int resolveTargetHits(SerializationContext context) {
        if (targetHits != null) return targetHits;
        if (totalTargetHits != null)
            return context.contentShareOf(totalTargetHits);
        return defaultTargetHits;
    }

}
