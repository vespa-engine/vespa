// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.ReferenceFieldValue;

/**
 * Model for ReferenceDataType which is more suitable when
 * we want to end up with NewDocumentType as target type.
 *
 * @author arnej
 **/
@SuppressWarnings("deprecation")
public final class NewDocumentReferenceDataType extends DataType {

    private StructuredDataType target;
    private DocumentType docTypeTarget = null;
    private ReferenceDataType delegate = null;

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

    public static NewDocumentReferenceDataType forDocumentName(String documentName) {
        return new NewDocumentReferenceDataType(buildTypeName(documentName),
                                                TemporaryStructuredDataType.create(documentName),
                                                true);
    }

    public NewDocumentReferenceDataType(DocumentType document) {
        this(buildTypeName(document.getName()), document, true);
        this.docTypeTarget = document;
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
            if ((docTypeTarget == null) && (type instanceof DocumentType)) {
                this.docTypeTarget = (DocumentType) type;
            }
        } else {
            throw new IllegalStateException
                (String.format("Unexpected attempt to replace already concrete target " +
                               "type in NewDocumentReferenceDataType instance (type is '%s')", target.getName()));
        }
    }

    @Override
    public FieldValue createFieldValue() {
        // TODO why do we even need this
        if (delegate == null) {
            if (docTypeTarget == null) {
                var tmptmp = TemporaryStructuredDataType.create(target.getName());
                var tmp = ReferenceDataType.createWithInferredId(tmptmp);
                return tmp.createFieldValue();
            }
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
}
