// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.search.dispatch.rpc.ProtobufSerialization;

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
    protected int numNegativeItems;
    protected int exclusionDistance;

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

    public void setNumNegativeItems(int numNegativeItems) {
        this.numNegativeItems = numNegativeItems;
    }

    public int getNumNegativeItems() {
        return numNegativeItems;
    }

    public void setExclusionDistance(int exclusionDistance) {
        this.exclusionDistance = exclusionDistance;
    }

    public int getExclusionDistance() {
        return exclusionDistance;
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
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context);
        IntegerCompressor.putCompressedPositiveNumber(distance, buffer);
        if (numNegativeItems != 0 && !ProtobufSerialization.isProtobufAlsoSerialized()) {
            throw new IllegalArgumentException("cannot serialize negative items in old protocol");
        }
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
        if (numNegativeItems != 0 || exclusionDistance != 0) {
            buffer.append(",");
            buffer.append(numNegativeItems);
            buffer.append(",");
            buffer.append(exclusionDistance);
        }
        buffer.append(")");
        buffer.append(" ");
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        NearItem other = (NearItem) object; // Ensured by superclass
        if (this.distance != other.distance) return false;
        if (this.numNegativeItems != other.numNegativeItems) return false;
        if (this.exclusionDistance != other.exclusionDistance) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), distance, numNegativeItems, exclusionDistance);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemNear.newBuilder();
        builder.setDistance(distance);
        builder.setNumNegativeTerms(numNegativeItems);
        builder.setExclusionDistance(exclusionDistance);
        for (var child : items()) {
            builder.addChildren(child.toProtobuf(context));
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemNear(builder.build())
                .build();
    }

}
