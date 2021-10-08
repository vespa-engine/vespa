// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Einar M R Rosenvinge
 */
class TemporaryDataType extends DataType {

    private final String detailedType;
    
    TemporaryDataType(int dataTypeId, String detailedType) {
        super("temporary_" + dataTypeId, dataTypeId);
        this.detailedType = detailedType;
    }

    @Override
    public FieldValue createFieldValue() {
        return null;
    }

    @Override
    public Class getValueClass() {
        return null;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        return false;
    }

    String getDetailedType() { return detailedType; }

}
