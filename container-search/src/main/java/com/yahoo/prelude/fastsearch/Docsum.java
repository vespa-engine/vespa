// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * An instance of a document summary, backed by binary data, which decodes and returns fields on request,
 * using the (shared) definition of this docsum.
 *
 * @author  <a href="mailt:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public final class Docsum {

    private final DocsumDefinition definition;
    private final byte[] packet;
    /** The offsets into the packet data of each field, given the fields index, computed lazily */
    private final int[] fieldOffsets;
    /** The largest stored offset */
    private int largestStoredOffset=-1;

    public Docsum(DocsumDefinition definition, byte[] packet) {
        this.definition = definition;
        this.packet = packet;
        fieldOffsets=new int[definition.getFieldCount()];
    }

    public DocsumDefinition getDefinition() { return definition; }

    public Integer getFieldIndex(String fieldName) {
        return definition.getFieldIndex(fieldName);
    }

    public Object decode(int fieldIndex) {
        ByteBuffer b=packetAsBuffer();
        setAndReturnOffsetToField(b, fieldIndex);
        return definition.getField(fieldIndex).decode(b);
    }

    /** Fetches the field as raw utf-8 if it is a text field. Returns null otherwise */
    public FastHit.RawField fetchFieldAsUtf8(int fieldIndex) {
        DocsumField dataType = definition.getField(fieldIndex);
        if ( ! (dataType instanceof LongstringField || dataType instanceof XMLField || dataType instanceof StringField))
            return null;

        ByteBuffer b=packetAsBuffer();
        DocsumField field = definition.getField(fieldIndex);
        int fieldStart = setAndReturnOffsetToField(b, fieldIndex); // set buffer.pos = start of field
        if (field.isCompressed(b)) return null;
        int length = field.getLength(b); // scan to end of field
        if (field instanceof VariableLengthField) {
            int fieldLength = ((VariableLengthField) field).sizeOfLength();
            b.position(fieldStart + fieldLength); // reset to start of field
            length -= fieldLength;
        } else {
            b.position(fieldStart); // reset to start of field
        }
        byte[] bufferView = new byte[length];
        b.get(bufferView);
        return new FastHit.RawField(dataType, bufferView);
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
