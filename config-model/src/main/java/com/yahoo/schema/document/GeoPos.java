// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;

/**
 * Common utilities for recognizing fields with the built-in "position" datatype,
 * possibly in array form.
 * @author arnej
 */
public class GeoPos {
    static public boolean isPos(DataType type) {
        return PositionDataType.INSTANCE.equals(type);
    }
    static public boolean isPosArray(DataType type) {
        return DataType.getArray(PositionDataType.INSTANCE).equals(type);
    }
    static public boolean isAnyPos(DataType type) {
        return isPos(type) || isPosArray(type);
    }

    static public boolean isPos(ImmutableSDField field)      { return isPos(field.getDataType()); }
    static public boolean isPosArray(ImmutableSDField field) { return isPosArray(field.getDataType()); }
    static public boolean isAnyPos(ImmutableSDField field)   { return isAnyPos(field.getDataType()); }
}
