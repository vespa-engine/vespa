// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.ReferenceFieldValue;

public class ReferenceDataType extends DataType {

    private final DocumentType targetType;

    public ReferenceDataType(DocumentType targetType, int id) {
        super(buildTypeName(targetType), id);
        this.targetType = targetType;
    }

    private ReferenceDataType(DocumentType targetType) {
        super(buildTypeName(targetType), 0);
        setId(getName().hashCode());
        this.targetType = targetType;
    }

    private static String buildTypeName(DocumentType targetType) {
        return "Reference<" + targetType.getName() + ">";
    }

    // Creates a new type where the numeric ID is based no the hash of targetType
    public static ReferenceDataType createWithInferredId(DocumentType targetType) {
        return new ReferenceDataType(targetType);
    }

    public DocumentType getTargetType() { return targetType; }

    @Override
    public ReferenceFieldValue createFieldValue() {
        return new ReferenceFieldValue(this);
    }

    @Override
    public Class<? extends ReferenceFieldValue> getValueClass() {
        return ReferenceFieldValue.class;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        if (value == null) {
            return false;
        }
        if (!ReferenceFieldValue.class.isAssignableFrom(value.getClass())) {
            return false;
        }
        ReferenceFieldValue rhs = (ReferenceFieldValue)value;
        return rhs.getDataType().equals(this);
    }
}
