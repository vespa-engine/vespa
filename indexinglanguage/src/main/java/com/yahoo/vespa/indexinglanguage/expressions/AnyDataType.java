package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * A DataType representing "any". This is (so far) only needed during type resolution of indexing pipelines
 * so it is placed here.
 *
 * @author bratseth
 */
class AnyDataType extends DataType {

    static final AnyDataType instance = new AnyDataType();

    private AnyDataType() {
        super("any", DataType.lastPredefinedDataTypeId() + 1);
    }

    @Override
    public FieldValue createFieldValue() { throw new UnsupportedOperationException(); }

    @Override
    public Class<?> getValueClass() { throw new UnsupportedOperationException(); }

    @Override
    public boolean isValueCompatible(FieldValue value) { return true; }

}
