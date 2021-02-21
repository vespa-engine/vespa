// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * A data type describing a field value having a reference to an annotation of a given type.
 *
 * @author Einar M R Rosenvinge
 */
public class AnnotationReferenceDataType extends DataType {

    private AnnotationType aType;

    /**
     * Creates an AnnotationReferenceDataType with a generated id.
     *
     * @param aType the annotation type that AnnotationRefs shall refer to
     */
    public AnnotationReferenceDataType(AnnotationType aType) {
        super("annotationreference<" + ((aType == null) ? "" : aType.getName()) + ">", 0);
        setAnnotationType(aType);
    }

    /**
     * Creates an AnnotationReferenceDataType with a given id.
     *
     * @param aType the annotation type that AnnotationRefs shall refer to
     * @param id    the id to use
     */
    public AnnotationReferenceDataType(AnnotationType aType, int id) {
        super("annotationreference<" + ((aType == null) ? "" : aType.getName()) + ">", id);
        this.aType = aType;
    }

    /**
     * Creates an AnnotationReferenceDataType. WARNING! Do not use!
     */
    protected AnnotationReferenceDataType() {
        super("annotationreference<>", 0);
    }

    private int createId() {
        //TODO: This should be Java's hashCode(), since all other data types use it, and using something else here will probably lead to collisions
        return getName().toLowerCase().hashCode();
    }

    @Override
    public FieldValue createFieldValue() {
        return new AnnotationReference(this);
    }

    @Override
    public Class getValueClass() {
        return AnnotationReference.class;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        if (!(value instanceof AnnotationReference)) {
            return false;
        }
        AnnotationReference reference = (AnnotationReference) value;
        if (equals(reference.getDataType())) {
            return true;
        }
        return false;
    }

    /**
     * Returns the annotation type of this AnnotationReferenceDataType.
     *
     * @return the annotation type of this AnnotationReferenceDataType.
     */
    public AnnotationType getAnnotationType() {
        return aType;
    }

    /**
     * Sets the annotation type that this AnnotationReferenceDataType points to. WARNING! Do not use.
     * @param type the annotation type of this AnnotationReferenceDataType.
     */
    protected void setAnnotationType(AnnotationType type) {
        this.aType = type;
        setId(createId());
    }

}
