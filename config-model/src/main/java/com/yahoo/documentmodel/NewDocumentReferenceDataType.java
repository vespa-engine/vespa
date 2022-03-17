// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author arnej
 */
@SuppressWarnings("deprecation")
public final class NewDocumentReferenceDataType extends DataType {

    private StructuredDataType target;
    private final boolean temporary;

    private NewDocumentReferenceDataType(NewDocumentType.Name nameAndId,
                                         StructuredDataType target,
                                         boolean temporary)
    {
        super(nameAndId.getName(), nameAndId.getId());
        this.target = target;
        this.temporary = temporary;
    }

    private static NewDocumentType.Name buildTypeName(String documentName) {
        String typeName = "Reference<" + documentName + ">";
        return new NewDocumentType.Name(typeName);
    }

    public NewDocumentReferenceDataType(String documentName) {
        this(buildTypeName(documentName), TemporaryStructuredDataType.create(documentName), true);
    }

    public NewDocumentReferenceDataType(NewDocumentType document) {
        this(buildTypeName(document.getName()), document, false);
    }

    public boolean isTemporary() { return temporary; }

    public StructuredDataType getTargetType() { return target; }

    public void setTargetType(StructuredDataType type) {
        assert(target.getName().equals(type.getName()));
        if (temporary) {
            this.target = type;
        } else {
            throw new IllegalStateException
                (String.format("Unexpected attempt to replace already concrete target " +
                               "type in ReferenceDataType instance (type is '%s')", target.getName()));
        }
    }

    @Override
    public FieldValue createFieldValue() {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public Class<FieldValue> getValueClass() {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public boolean equals(Object rhs) {
        if (rhs instanceof NewDocumentReferenceDataType) {
            var other = (NewDocumentReferenceDataType) rhs;
            return super.equals(other) && (temporary == other.temporary) && target.equals(other.target);
        }
        return false;
    }
}
