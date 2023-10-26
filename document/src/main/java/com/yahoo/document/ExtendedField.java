// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

/**
 * This adds an Extractor to the Field that can be used to access the backed value
 * used in the concrete document types.
 * @author baldersheim
 */
public class ExtendedField extends Field {
    public interface Extract {
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
        extract.set(doc, (fv == null) ? null : fv.getWrappedValue());
        return old;
    }
}
