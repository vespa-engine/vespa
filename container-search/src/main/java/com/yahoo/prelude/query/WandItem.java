// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;

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

    private int targetNumHits;
    private double scoreThreshold = 0;
    private double thresholdBoostFactor = 1;

    /**
     * Creates an empty WandItem.
     *
     * @param fieldName The name of the weighted set field to search with this WandItem.
     * @param targetNumHits The target for minimum number of hits to produce by the backend search operator handling this WandItem.
     */
    public WandItem(String fieldName, int targetNumHits) {
        super(fieldName);
        this.targetNumHits = targetNumHits;
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

    public int getTargetNumHits() {
        return targetNumHits;
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
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        IntegerCompressor.putCompressedPositiveNumber(targetNumHits, buffer);
        buffer.putDouble(scoreThreshold);
        buffer.putDouble(thresholdBoostFactor);
    }

    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        buffer.append("(");
        buffer.append(targetNumHits);
        buffer.append(",");
        buffer.append(scoreThreshold);
        buffer.append(",");
        buffer.append(thresholdBoostFactor);
        buffer.append(")");
        buffer.append(" ");
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("targetNumHits", targetNumHits);
        discloser.addProperty("scoreThreshold", scoreThreshold);
        discloser.addProperty("thresholdBoostFactor", thresholdBoostFactor);
    }

}
