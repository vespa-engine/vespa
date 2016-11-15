package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

/**
 * Created by balder on 14/11/2016.
 */
public class ExtendedField extends Field {
    public static interface Extract {
        Object get(StructuredFieldValue doc);
        void set(StructuredFieldValue doc, Object value);
    }
    private final Extract extract;
    public ExtendedField(String name, DataType type, Extract extract) {
        super(name, type);
        this.extract = extract;
    }
    public FieldValue getFieldValue(StructuredFieldValue doc) {
        Object raw = extract.get(doc);
        return raw == null ? null : getDataType().createFieldValue(raw);
    }
    public FieldValue setFieldValue(StructuredFieldValue doc, FieldValue fv) {
        FieldValue old = getFieldValue(doc);
        extract.set(doc, fv.getWrappedValue());
        return old;
    }
}
