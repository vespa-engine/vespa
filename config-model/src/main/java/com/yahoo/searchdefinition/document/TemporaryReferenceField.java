// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.document.DataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * Represents a reference field inside a SD document. This is temporary AST structure that only referes to the
 * target document type by name ({@link TemporaryStructuredDataType}.
 *
 * @author bjorncs
 */
public class TemporaryReferenceField extends DataType {

    private final TemporaryStructuredDataType referencedDocument;

    public TemporaryReferenceField(TemporaryStructuredDataType referencedDocument) {
        super(createName(referencedDocument), 0);
        this.referencedDocument = referencedDocument;
    }

    private static String createName(TemporaryStructuredDataType referenceType) {
        return "reference<" + referenceType.getName() + ">";
    }

    public TemporaryStructuredDataType getReferencedDocument() {
        return referencedDocument;
    }

    @Override
    public FieldValue createFieldValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class getValueClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        throw new UnsupportedOperationException();
    }
}
