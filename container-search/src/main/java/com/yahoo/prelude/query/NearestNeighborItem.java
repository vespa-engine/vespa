// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import com.google.common.annotations.Beta;
import com.yahoo.compress.IntegerCompressor;

import java.nio.ByteBuffer;

/**
 * Represent a query item matching the K nearest neighbors in a multi-dimensional vector space.
 * The query point vector is referenced by the name of a tensor passed as a query rank feature;
 * specifying "myvector" as the name means the query must set "ranking.features.query(myvector)".
 * This rank feature must be configured with the correct tensor type in the active query profile.
 * The field name (AKA the index name) given must be an attribute, with the exact same tensor type.
 *
 * @author arnej
 */
@Beta
public class NearestNeighborItem extends SimpleTaggableItem {

    private int targetNumHits = 0;
    private String field;
    private String queryTensorName;

    public NearestNeighborItem(String fieldName, String queryTensorName) {
        this.field = fieldName;
        this.queryTensorName = queryTensorName;
    }

    /** Returns the K number of hits to produce */
    public int getTargetNumHits() { return targetNumHits; }

    /** Returns the field name */
    public String getIndexName() { return field; }

    /** Returns the name of the query tensor */
    public String getQueryTensorName() { return queryTensorName; }

    /** Set the K number of hits to produce */
    public void setTargetNumHits(int target) { this.targetNumHits = target; }

    @Override
    public void setIndexName(String index) { this.field = index; }

    @Override
    public ItemType getItemType() { return ItemType.NEAREST_NEIGHBOR; }

    @Override
    public String getName() { return "NEAREST_NEIGHBOR"; }

    @Override
    public int getTermCount() { return 1; }

    @Override
    public int encode(ByteBuffer buffer) {
        super.encodeThis(buffer);
        putString(field, buffer);
        putString(queryTensorName, buffer);
        IntegerCompressor.putCompressedPositiveNumber(targetNumHits, buffer);
        return 1;  // number of encoded stack dump items
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append("{field=").append(field);
        buffer.append(",queryTensorName=").append(queryTensorName);
        buffer.append(",targetNumHits=").append(targetNumHits).append("}");
    }
}
