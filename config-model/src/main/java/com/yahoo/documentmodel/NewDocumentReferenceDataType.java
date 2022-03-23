// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.ReferenceFieldValue;

/**
 * Model for ReferenceDataType which is more suitable when
 * we want to end up with NewDocumentType as target type.
 *
 * @author arnej
 **/
public final class NewDocumentReferenceDataType extends DataType {

    private final StructuredDataType target;
    private final DocumentType docTypeTarget;
    private ReferenceDataType delegate = null;

    private final boolean temporary;

    private NewDocumentReferenceDataType(NewDocumentType.Name nameAndId,
                                         StructuredDataType target,
                                         DocumentType docTypeTarget,
                                         boolean temporary)
    {
        super(nameAndId.getName(), nameAndId.getId());
        this.target = target;
        this.docTypeTarget = docTypeTarget;
        this.temporary = temporary;
    }

    private static NewDocumentType.Name buildTypeName(String documentName) {
        String typeName = "Reference<" + documentName + ">";
        return new NewDocumentType.Name(typeName);
    }

    public static NewDocumentReferenceDataType forDocumentName(String documentName) {
        return new NewDocumentReferenceDataType(new DocumentType(documentName));
    }

    public NewDocumentReferenceDataType(DocumentType document) {
        this(buildTypeName(document.getName()), document, document, true);
    }

    public NewDocumentReferenceDataType(NewDocumentType document) {
        this(buildTypeName(document.getName()), document, new DocumentType(document.getName()), false);
    }

    public boolean isTemporary() { return temporary; }

    public StructuredDataType getTargetType() { return target; }
    public String getTargetTypeName() { return target.getName(); }
    public int getTargetTypeId() { return target.getId(); }

    @Override
    public FieldValue createFieldValue() {
        // TODO why do we even need this
        if (delegate == null) {
            delegate = ReferenceDataType.createWithInferredId(docTypeTarget);
        }
        return delegate.createFieldValue();
    }

    @Override
    public Class<? extends ReferenceFieldValue> getValueClass() {
        return ReferenceFieldValue.class;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        var dt = value.getDataType();
        if (dt instanceof ReferenceDataType) {
            var refType = (ReferenceDataType) dt;
            var docTypeName = refType.getTargetType().getName();
            return docTypeName.equals(target.getName());
        }
        return false;
    }

    @Override
    public boolean equals(Object rhs) {
        if (rhs instanceof NewDocumentReferenceDataType) {
            var other = (NewDocumentReferenceDataType) rhs;
            return super.equals(other) && (temporary == other.temporary) && target.equals(other.target);
        }
        return false;
    }

    @Override
    public String toString() {
        return "{NDRTDT " + getName() + " id=" + getId() + " target=" + target + " [" + target.getClass().getSimpleName() + "]}";
    }

}
