// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An integer item which only consists of a value and weight.
 *
 * @author baldersheim
 */
public class PureWeightedInteger extends PureWeightedItem  {

    private final long value;

    public PureWeightedInteger(long value) {
        this(value, 100);
    }
    public PureWeightedInteger(long value, int weight) {
        super(weight);
        this.value = value;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.PURE_WEIGHTED_INTEGER;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        buffer.putLong(value);
    }

    @Override
    public int getTermCount() {
        return 1;
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append(value);
        super.appendBodyString(buffer);
    }

    public long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if ( ! super.equals(other)) return false;
        return value == ((PureWeightedInteger)other).value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.setValue(value);
    }

}
