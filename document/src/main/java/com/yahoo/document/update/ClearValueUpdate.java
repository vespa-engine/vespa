// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;

/**
 * <p>Value update that represents clearing a field. Clearing a field mean removing it.</p>
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ClearValueUpdate extends ValueUpdate {

    public ClearValueUpdate() {
        super(ValueUpdateClassID.CLEAR);
    }

    @Override
    public FieldValue applyTo(FieldValue fval) {
        return null;
    }

    @Override
    protected void checkCompatibility(DataType fieldType) {
        // empty
    }

    @Override
    public FieldValue getValue() {
        return null;
    }

    @Override
    public void setValue(FieldValue value) {
        // empty
    }

    @Override
    public void serialize(DocumentUpdateWriter data, DataType superType) {
        data.write(this, superType);
    }
}
