// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import java.nio.ByteBuffer;

/**
 * @author baldersheim
 */
public interface Serializer {

    Serializer putByte(FieldBase field, byte value);
    Serializer putShort(FieldBase field, short value);
    Serializer putInt(FieldBase field, int value);
    Serializer putLong(FieldBase field, long value);
    Serializer putFloat(FieldBase field, float value);
    Serializer putDouble(FieldBase field, double value);
    Serializer put(FieldBase field, byte[] value);
    Serializer put(FieldBase field, ByteBuffer value);
    Serializer put(FieldBase field, String value);

}
