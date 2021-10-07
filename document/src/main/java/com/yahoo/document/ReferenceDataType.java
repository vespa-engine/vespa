// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import com.yahoo.vespa.objects.Ids;

/**
 * A <code>ReferenceDataType</code> specifies a particular concrete document type that a
 * {@link ReferenceFieldValue} instance binds to.
 *
 * @author vekterli
 */
public class ReferenceDataType extends DataType {

    // Magic number for Identifiable, see document/util/identifiable.h
    public static final int classId = registerClass(Ids.document + 68, ReferenceDataType.class);

    private StructuredDataType targetType;

    public ReferenceDataType(DocumentType targetType, int id) {
        this((StructuredDataType)targetType, id);
    }

    /**
     * Constructor used when building a multi document type model where the concrete instance
     * of the target document type might not yet be known. The temporary data type should be
     * replaced later using setTargetType().
     */
    public ReferenceDataType(TemporaryStructuredDataType temporaryTargetType, int id) {
        this((StructuredDataType) temporaryTargetType, id);
    }

    private ReferenceDataType(StructuredDataType targetType, int id) {
        super(buildTypeName(targetType), id);
        this.targetType = targetType;
    }

    private ReferenceDataType(StructuredDataType targetType) {
        this(targetType, buildTypeName(targetType).hashCode());
    }

    private static String buildTypeName(StructuredDataType targetType) {
        return "Reference<" + targetType.getName() + ">";
    }

    /**
     * Creates a new type where the numeric ID is based on the hash of targetType
     */
    public static ReferenceDataType createWithInferredId(DocumentType targetType) {
        return new ReferenceDataType(targetType);
    }

    /**
     * Creates a new type where the numeric ID is based on the hash of targetType
     */
    public static ReferenceDataType createWithInferredId(TemporaryStructuredDataType targetType) {
        return new ReferenceDataType(targetType);
    }

    public StructuredDataType getTargetType() { return targetType; }

    /**
     * Overrides the stored temporary data type with a concrete StructuredDataType instance. Should only
     * be invoked from configuration or model code when resolving temporary types.
     *
     * @throws IllegalStateException if the previously stored target type is already a concrete
     *     instance (not TemporaryStructuredDataType).
     */
    public void setTargetType(StructuredDataType targetType) {
        if (! (this.targetType instanceof TemporaryStructuredDataType)) {
            throw new IllegalStateException(String.format(
                    "Unexpected attempt to replace already concrete target " +
                    "type in ReferenceDataType instance (type is '%s')", this.targetType.getName()));
        }
        this.targetType = targetType;
        setName(buildTypeName(targetType));
    }

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

    private int compareTargetType(DataType rhs) {
        return (rhs instanceof ReferenceDataType) ? targetType.compareTo(((ReferenceDataType) rhs).targetType) : 0;
    }

    @Override
    public int compareTo(DataType rhs) {
        int cmp = super.compareTo(rhs);
        return (cmp != 0) ? cmp : compareTargetType(rhs);
    }

    @Override
    public boolean equals(Object rhs) {
        return  super.equals(rhs)
                && (rhs instanceof ReferenceDataType)
                && targetType.equals(((ReferenceDataType) rhs).targetType);
    }
}
