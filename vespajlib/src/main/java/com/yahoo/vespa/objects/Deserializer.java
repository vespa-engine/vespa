// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

/**
 * @author baldersheim
 */
public interface Deserializer {

    byte getByte(FieldBase field);
    short getShort(FieldBase field);
    int getInt(FieldBase field);
    long getLong(FieldBase field);
    float getFloat(FieldBase field);
    double getDouble(FieldBase field);
    byte [] getBytes(FieldBase field, int length);
    String getString(FieldBase field);

}
