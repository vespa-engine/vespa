// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;


/**
 * A set of terms which must be near each other to match.
 *
 * @author bratseth
 * @author havardpe
 */
public class NearItem extends CompositeItem {

    protected int distance;

    /** The default distance used if none is specified: 2 */
    public static final int defaultDistance = 2;

    /** Creates a NEAR item with distance 2 */
    public NearItem() {
        setDistance(defaultDistance);
    }

    /**
     * Creates a <i>near</i> item with a limit to the distance between the words.
     *
     * @param distance the maximum position difference between the words which should be counted as a match
     */
    public NearItem(int distance) {
        setDistance(distance);
    }

    public void setDistance(int distance) {
        if (distance < 0)
            throw new IllegalArgumentException("Can not use negative distance " + distance);
        this.distance = distance;
    }

    public int getDistance() {
        return distance;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.NEAR;
    }

    @Override
    public String getName() {
        return "NEAR";
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        IntegerCompressor.putCompressedPositiveNumber(distance, buffer);
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("limit", distance);
    }

    /** Appends the heading of this string - <code>[getName()]([limit]) </code> */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        buffer.append("(");
        buffer.append(distance);
        buffer.append(")");
        buffer.append(" ");
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        NearItem other = (NearItem) object; // Ensured by superclass
        if (this.distance != other.distance) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), distance);
    }

}
