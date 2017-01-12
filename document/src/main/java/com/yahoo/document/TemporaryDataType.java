// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class TemporaryDataType extends DataType {
    TemporaryDataType(int dataTypeId) {
        super("temporary_" + dataTypeId, dataTypeId);
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
}
