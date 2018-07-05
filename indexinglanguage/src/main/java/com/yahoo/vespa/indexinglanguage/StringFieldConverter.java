// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public abstract class StringFieldConverter extends FieldValueConverter {

    @Override
    protected final boolean shouldConvert(FieldValue value) {
        return value.getDataType().equals(DataType.STRING);
    }

    @Override
    protected final FieldValue doConvert(FieldValue value) {
        return doConvert((StringFieldValue)value);
    }

    protected abstract FieldValue doConvert(StringFieldValue value);
}
