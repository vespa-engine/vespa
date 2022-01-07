// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access;

/**
 * Enumeration of all possible types accessed by the Inspector API.
 * Note that:
 * - the EMPTY type is used as a placeholder where data is missing.
 * - all integers are put into LONGs; the encoding takes care of
 *   packing small integers compactly so this is also efficient.
 * - likeweise DOUBLE is the only floating-point type, but "simple"
 *   numbers (like 0.0 or 1.0) are packed compactly anyway.
 * - DATA can be used anything for wrapping anything else serialized
 *   as an array of bytes.
 * - maps should be represented as an ARRAY of OBJECTs where each
 *   object has the fields "key" and "value".
 */
public enum Type {

    EMPTY, BOOL, LONG, DOUBLE, STRING, DATA, ARRAY, OBJECT;

}
