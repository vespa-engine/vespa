package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;

/**
 * Created by balder on 14/11/2016.
 */
class ExtendedField extends Field {
    static interface Extract {
        Object get(Document doc);
    }
    private final Extract extract;
    ExtendedField(String name, DataType type, Extract extract) {
        super(name, type);
        this.extract = extract;
    }
    FieldValue getFieldValue(Document doc) {
        Object raw = extract.get(doc);
        return raw == null ? null : getDataType().createFieldValue(raw);
    }
}
