// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * A weighted set query item to be evaluated as a Wand with dot product scoring.
 *
 * The dot product is calculated between the matched tokens of the weighted set field searched
 * and the weights associated with the tokens of this WandItem.
 * The resulting dot product will be available as a raw score in the rank framework.
 *
 * @author geirst
 */
public class WandItem extends WeightedSetItem {

    /** The default targetHits used if none is specified: 100 */
    private static final int defaultTargetHits = 100;

    private Integer targetHits;
    private Integer totalTargetHits = null;
    private double scoreThreshold = 0;
    private double thresholdBoostFactor = 1;

    /**
     * Creates an empty WandItem.
     *
     * @param fieldName the name of the weighted set field to search with this WandItem.
     */
    public WandItem(String fieldName) {
        super(fieldName);
    }

    /** @deprecated set totalTargetHits instead. */
    @Deprecated // TODO: Remove on Vespa 9
    public WandItem(String fieldName, int targetHits) {
        this(fieldName);
        this.targetHits = targetHits;
    }

    /** @deprecated set totalTargetHits instead. */
    @Deprecated // TODO: Remove on Vespa 9
    public WandItem(String fieldName, Integer targetHits) {
        this(fieldName);
        this.targetHits = targetHits;
    }

    /**
     * Creates an empty WandItem.
     *
     * @param fieldName the name of the weighted set field to search with this WandItem.
     * @param tokens the tokens to search for
     */
    public WandItem(String fieldName, Map<Object, Integer> tokens) {
        super(fieldName, tokens);
    }

    /** @deprecated set totalTargetHits instead. */
    @Deprecated // TODO: Remove on Vespa 9
    public WandItem(String fieldName, Integer targetHits, Map<Object, Integer> tokens) {
        this(fieldName, tokens);
        this.targetHits = targetHits;
    }

    /** @deprecated set totalTargetHits instead. */
    @Deprecated // TODO: Remove on Vespa 9
    public WandItem(String fieldName, int targetHits, Map<Object, Integer> tokens) {
        this(fieldName, tokens);
        this.targetHits = targetHits;
    }

    /**
     * Sets the initial score threshold used by the backend search operator handling this WandItem.
     * The score of a document must be larger than this threshold in order to be considered a match.
     * Default value is 0.0.
     *
     * @param scoreThreshold the initial score threshold.
     */
    public void setScoreThreshold(double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * Sets the boost factor used by the backend search operator to boost the threshold before
     * comparing it with the upper bound score of the document being evaluated.
     * A large value of this factor results in fewer full evaluations and in an expected loss in precision.
     * Similarly, a gain in performance might be expected. Default value is 1.0.
     *
     * NOTE: This boost factor is only used when this WandItem is searching a Vespa field.
     *
     * @param thresholdBoostFactor the boost factor.
     */
    public void setThresholdBoostFactor(double thresholdBoostFactor) {
        this.thresholdBoostFactor = thresholdBoostFactor;
    }

    /** Returns the number of hits to produce per node, or null if not set. */
    public Integer getTargetHits() { return targetHits; }

    /** Returns the total number of hits to produce across all nodes, or null if not set. */
    public Integer getTotalTargetHits() { return totalTargetHits; }

    /** Set the number of hits to produce per node. */
    public void setTargetHits(Integer target) { this.targetHits = target; }

    /** Set the total number of hits to produce across all nodes. */
    public void setTotalTargetHits(Integer total) { this.totalTargetHits = total; }

    /** @deprecated use {@link #getTargetHits()} */
    @Deprecated // TODO Vespa 9: Remove
    public int getTargetNumHits() {
        return targetHits != null ? targetHits : defaultTargetHits;
    }

    public double getScoreThreshold() {
        return scoreThreshold;
    }

    public double getThresholdBoostFactor() {
        return thresholdBoostFactor;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.WAND;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context);
        IntegerCompressor.putCompressedPositiveNumber(resolveTargetHits(context), buffer);
        buffer.putDouble(scoreThreshold);
        buffer.putDouble(thresholdBoostFactor);
    }

    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        if (targetHits != null) {
            buffer.append("(");
            buffer.append(targetHits);
            buffer.append(")");
        }
        if (totalTargetHits != null || scoreThreshold != 0 || thresholdBoostFactor != 1) {
            buffer.append(" {");
            if (totalTargetHits != null)
                buffer.append("totalTargetHits=").append(totalTargetHits).append(", ");
            if (scoreThreshold != 0)
                buffer.append("scoreThreshold=").append(scoreThreshold).append(", ");
            if (thresholdBoostFactor != 0)
                buffer.append("thresholdBoostFactor=").append(thresholdBoostFactor).append(", ");
            buffer.setLength(buffer.length() - ", ".length());
            buffer.append("}");
        }
        buffer.append(" ");
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        if (targetHits != null)
            discloser.addProperty("targetHits", targetHits);
        if (totalTargetHits != null)
            discloser.addProperty("totalTargetHits", totalTargetHits);
        if (scoreThreshold != 0)
            discloser.addProperty("scoreThreshold", scoreThreshold);
        if (thresholdBoostFactor != 1)
            discloser.addProperty("thresholdBoostFactor", thresholdBoostFactor);
    }

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        var other = (WandItem)o;
        if ( ! Objects.equals(this.targetHits, other.targetHits)) return false;
        if ( ! Objects.equals(this.totalTargetHits, other.totalTargetHits)) return false;
        if ( this.scoreThreshold != other.scoreThreshold) return false;
        if ( this.thresholdBoostFactor != other.thresholdBoostFactor) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetHits, totalTargetHits, scoreThreshold, thresholdBoostFactor);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        int resolvedTargetHits = resolveTargetHits(context);
        if (hasOnlyLongs()) {
            var builder = SearchProtocol.ItemLongWand.newBuilder();
            builder.setProperties(ToProtobuf.buildTermProperties(this, getIndexName()));
            builder.setTargetNumHits(resolvedTargetHits);
            builder.setScoreThreshold(scoreThreshold);
            builder.setThresholdBoostFactor(thresholdBoostFactor);
            for (var it = getTokens(); it.hasNext();) {
                var entry = it.next();
                var weightedLong = SearchProtocol.PureWeightedLong.newBuilder()
                        .setWeight(entry.getValue())
                        .setValue((Long) entry.getKey())
                        .build();
                builder.addWeightedLongs(weightedLong);
            }
            return SearchProtocol.QueryTreeItem.newBuilder()
                    .setItemLongWand(builder.build())
                    .build();
        } else {
            var builder = SearchProtocol.ItemStringWand.newBuilder();
            builder.setProperties(ToProtobuf.buildTermProperties(this, getIndexName()));
            builder.setTargetNumHits(resolvedTargetHits);
            builder.setScoreThreshold(scoreThreshold);
            builder.setThresholdBoostFactor(thresholdBoostFactor);
            for (var it = getTokens(); it.hasNext();) {
                var entry = it.next();
                var weightedString = SearchProtocol.PureWeightedString.newBuilder()
                        .setWeight(entry.getValue())
                        .setValue(entry.getKey().toString())
                        .build();
                builder.addWeightedStrings(weightedString);
            }
            return SearchProtocol.QueryTreeItem.newBuilder()
                    .setItemStringWand(builder.build())
                    .build();
        }
    }

    private int resolveTargetHits(SerializationContext context) {
        if (targetHits != null) return targetHits;
        if (totalTargetHits != null)
            return context.contentShareOf(totalTargetHits);
        return defaultTargetHits;
    }

}
