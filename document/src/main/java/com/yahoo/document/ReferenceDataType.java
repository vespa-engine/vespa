// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.ReferenceFieldValue;

/**
 * A <code>ReferenceDataType</code> specifies a particular concrete document type that a
 * {@link ReferenceFieldValue} instance binds to.
 *
 * @author vekterli
 * @since 6.65
 */
public class ReferenceDataType extends DataType {

    private final DocumentType targetType;

    public ReferenceDataType(DocumentType targetType, int id) {
        super(buildTypeName(targetType), id);
        this.targetType = targetType;
    }

    private ReferenceDataType(DocumentType targetType) {
        this(targetType, buildTypeName(targetType).hashCode());
    }

    private static String buildTypeName(DocumentType targetType) {
        return "Reference<" + targetType.getName() + ">";
    }

    // Creates a new type where the numeric ID is based on the hash of targetType
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
