// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Helper interface for inserting values into any of the container
 * classes (ArrayValue, ObjectValue, or Slime).  May be useful for
 * deserializers where you can use it to decouple the actual value
 * decoding from the container where the value should be inserted.
 */
public interface Inserter {

    Cursor insertNIX();
    Cursor insertBOOL(boolean value);
    Cursor insertLONG(long value);
    Cursor insertDOUBLE(double value);
    Cursor insertSTRING(String value);
    Cursor insertSTRING(byte[] utf8);
    Cursor insertDATA(byte[] value);
    Cursor insertARRAY();
    Cursor insertOBJECT();

}
