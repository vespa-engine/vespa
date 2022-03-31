// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.objects.Ids;

/**
 * @author Einar M R Rosenvinge
 */
public class NumericDataType extends PrimitiveDataType {

    // The global class identifier shared with C++.
    public static int classId = registerClass(Ids.document + 52, NumericDataType.class);
    /**
     * Creates a datatype
     *
     * @param name      the name of the type
     * @param code      the code (id) of the type
     * @param type      the field value used for this type
     */
    protected NumericDataType(java.lang.String name, int code, Class<? extends FieldValue> type, Factory factory) {
        super(name, code, type, factory);
    }

    @Override
    public NumericDataType clone() {
        return (NumericDataType) super.clone();
    }

}
