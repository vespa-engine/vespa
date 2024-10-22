// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.Array;
import com.yahoo.vespa.objects.Ids;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public class ArrayDataType extends CollectionDataType {

    // The global class identifier shared with C++.
    public static int classId = registerClass(Ids.document + 54, ArrayDataType.class);

    public ArrayDataType(DataType nestedType) {
        super("Array<"+nestedType.getName()+">", 0, nestedType);
        setId(getName().toLowerCase().hashCode());
    }

    public ArrayDataType(DataType nestedType, int code) {
        super("Array<"+nestedType.getName()+">", code, nestedType);
    }

    public ArrayDataType clone() {
        return (ArrayDataType) super.clone();
    }

    public Array createFieldValue() {
        return new Array(this);
    }

    @Override
    public Class getValueClass() {
        return Array.class;
    }

    @Override
    public FieldPath buildFieldPath(String remainFieldName)
    {
        if (remainFieldName.length() > 0 && remainFieldName.charAt(0) == '[') {
            int endPos = remainFieldName.indexOf(']');
            if (endPos == -1) {
                throw new IllegalArgumentException("Array subscript must be closed with ]");
            } else {
                FieldPath path = getNestedType().buildFieldPath(skipDotInString(remainFieldName, endPos));
                List<FieldPathEntry> tmpPath = new ArrayList<FieldPathEntry>(path.getList());
                if (remainFieldName.charAt(1) == '$') {
                    tmpPath.add(0, FieldPathEntry.newVariableLookupEntry(remainFieldName.substring(2, endPos), getNestedType()));
                } else {
                    tmpPath.add(0, FieldPathEntry.newArrayLookupEntry(Integer.parseInt(remainFieldName.substring(1, endPos)), getNestedType()));
                }

                return new FieldPath(tmpPath);
            }
        }

        return getNestedType().buildFieldPath(remainFieldName);
    }

}
