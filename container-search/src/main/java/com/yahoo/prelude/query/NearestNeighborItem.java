// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import java.nio.ByteBuffer;

/**
 *
 * @author arnej
 */
public class NearestNeighborItem extends SimpleTaggableItem {

    private int targetNumber = 1000;
    private String field;
    private String property;

    public int getTargetNumHits() { return targetNumber; }
    public void setTargetNumHits(int target) { this.targetNumber = target; }

    public String getQueryProperty() { return property; }

    public NearestNeighborItem(String fieldName, String queryProperty) {
        this.field = fieldName;
        this.property = queryProperty;
    }

    public String getIndexName() { return field; }

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
        putString(property, buffer);
        buffer.putInt(targetNumber);
        return 1;  // number of encoded stack dump items
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append("{field=").append(field);
        buffer.append(",property=").append(property);
        buffer.append(",targetnumhits=").append(targetNumber).append("}");
    }
}
