// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * An instance of a document summary, backed by binary data, which decodes and returns fields on request,
 * using the (shared) definition of this docsum.
 *
 * @author Steinar Knutsen
 */
public final class Docsum {

    private final DocsumDefinition definition;
    private final byte[] packet;
    /** The offsets into the packet data of each field, given the fields index, computed lazily */
    private final int[] fieldOffsets;
    /** The largest stored offset */
    private int largestStoredOffset = -1;

    public Docsum(DocsumDefinition definition, byte[] packet) {
        this.definition = definition;
        this.packet = packet;
        fieldOffsets=new int[definition.getFieldCount()];
    }

    public DocsumDefinition getDefinition() { return definition; }

    public Object decode(int fieldIndex) {
        ByteBuffer b=packetAsBuffer();
        setAndReturnOffsetToField(b, fieldIndex);
        return definition.getField(fieldIndex).decode(b);
    }

    public ByteBuffer packetAsBuffer() {
        ByteBuffer buffer = ByteBuffer.wrap(packet);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.getInt(); // Skip class id
        return buffer;
    }

    /** Returns the offset of a given field in the buffer, and sets the position of the buffer to that field start */
    private int setAndReturnOffsetToField(ByteBuffer b, int fieldIndex) {
        // find and store missing offsets up to fieldIndex
        if (largestStoredOffset<0) { // initial case
            fieldOffsets[0]=b.position();
            largestStoredOffset++;
        }
        while (largestStoredOffset < fieldIndex) { // induction
            int offsetOfLargest=fieldOffsets[largestStoredOffset];
            b.position(offsetOfLargest);
            fieldOffsets[largestStoredOffset+1]=offsetOfLargest+definition.getField(largestStoredOffset).getLength(b);
            largestStoredOffset++;
        }

        // return the stored offset
        int offset=fieldOffsets[fieldIndex];
        b.position(offset);
        return offset;
    }

    public String toString() {
        return "docsum [definition: " + definition + "]";
    }

}
