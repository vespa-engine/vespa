// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;

/**
 * A DataType representing any numeric type. This is (so far) only needed during type resolution of indexing pipelines
 * so it is placed here.
 *
 * @author bratseth
 */
class AnyNumericDataType extends NumericDataType {

    static final AnyNumericDataType instance = new AnyNumericDataType();

    private AnyNumericDataType() {
        super("numeric", DataType.lastPredefinedDataTypeId() + 2, DoubleFieldValue.class, new UnsupportedFactory());
    }

    @Override
    public boolean isAssignableFrom(DataType other) {
        return other instanceof NumericDataType;
    }

    @Override
    public boolean isAssignableTo(DataType other) {
        return other instanceof AnyNumericDataType || other instanceof AnyDataType;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        return isAssignableFrom(value.getDataType());
    }

    @Override
    public FieldValue createFieldValue() { throw new UnsupportedOperationException(); }

    private static class UnsupportedFactory extends Factory {

        public FieldValue create() {
            throw new UnsupportedOperationException();
        }

        public FieldValue create(String value) {
            throw new UnsupportedOperationException();
        }

    }
}
