package com.yahoo.searchdefinition.document;

import com.yahoo.document.DataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.document.datatypes.FieldValue;

/**
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
