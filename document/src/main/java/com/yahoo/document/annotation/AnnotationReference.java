// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.vespa.objects.Ids;

/**
 * A FieldValue which holds a reference to an annotation of a specified type.
 *
 * @see Annotation#setFieldValue(com.yahoo.document.datatypes.FieldValue)
 * @author Einar M R Rosenvinge
 */
public class AnnotationReference extends FieldValue {

    public static int classId = registerClass(Ids.annotation + 2, AnnotationReference.class);
    private Annotation reference;
    private AnnotationReferenceDataType dataType;

    /**
     * Constructs a new AnnotationReference, with a reference to the given {@link Annotation}.
     *
     * @param type      the data type of this AnnotationReference
     * @param reference the reference to set
     * @throws IllegalArgumentException if the given annotation has a type that is not compatible with this reference
     */
    public AnnotationReference(AnnotationReferenceDataType type, Annotation reference) {
        this.dataType = type;
        setReference(reference);
    }

    /**
     * Constructs a new AnnotationReference.
     *
     * @param type the data type of this AnnotationReference
     */
    public AnnotationReference(AnnotationReferenceDataType type) {
        this(type, null);
    }

    /**
     * Clones this AnnotationReference.&nbsp;Note: No deep-copying, so the AnnotationReference returned
     * refers to the same Annotation as this AnnotationReference.
     *
     * @return a copy of this object, referring to the same Annotation instance.
     */
    @Override
    public AnnotationReference clone() {
        return (AnnotationReference) super.clone();
        //do not clone annotation that we're referring to. See wizardry in SpanTree for that.
    }

    /**
     * Returns the Annotation that this AnnotationReference refers to.
     *
     * @return the Annotation that this AnnotationReference refers to.
     */
    public Annotation getReference() {
        return reference;
    }

    @Override
    public void assign(Object o) {
        if (o != null && (!(o instanceof Annotation))) {
            throw new IllegalArgumentException("Cannot assign object of type " + o.getClass().getName() + " to an AnnotationReference, must be of type " + Annotation.class.getName());
        }
        setReference((Annotation) o);
    }

    /**
     * Set an {@link Annotation} that this AnnotationReference shall refer to.
     *
     * @param reference an Annotation that this AnnotationReference shall refer to.
     * @throws IllegalArgumentException if the given annotation has a type that is not compatible with this reference
     */
    public void setReference(Annotation reference) {
        if (reference == null) {
            this.reference = null;
            return;
        }
        AnnotationReferenceDataType type = getDataType();
        if (type.getAnnotationType().isValueCompatible(reference)
                // The case if concrete annotation type
                || reference.getType() instanceof AnnotationType) {
            this.reference = reference;
        } else {
            throw new IllegalArgumentException("Cannot set reference, must be of type " + type + " (was of type " + reference.getType() + ")");
        }
    }


    /**
     * WARNING!&nbsp;Only to be used by deserializers when reference is not fully deserialized yet!&nbsp;Sets
     * an {@link Annotation} that this AnnotationReference shall refer to.
     *
     * @param reference an Annotation that this AnnotationReference shall refer to.
     * @throws IllegalArgumentException if the given annotation has a type that is not compatible with this reference
     */
    public void setReferenceNoCompatibilityCheck(Annotation reference) {
        if (reference == null) {
            this.reference = null;
            return;
        }
        this.reference = reference;
    }

    @Override
    public AnnotationReferenceDataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        if (dataType instanceof AnnotationReferenceDataType) {
            this.dataType = (AnnotationReferenceDataType) dataType;
        } else {
            throw new IllegalArgumentException("Cannot set dataType to " + dataType + ", must be of type AnnotationReferenceDataType.");
        }
    }

    @Override
    public void printXml(XmlStream xml) {
        // TODO: Implement AnnotationReference.printXml()
    }

    @Override
    public void clear() {
        this.reference = null;
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnotationReference)) return false;
        if (!super.equals(o)) return false;

        AnnotationReference that = (AnnotationReference) o;

        if (reference != null ? !reference.toString().equals(that.reference.toString()) : that.reference != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (reference != null ? reference.toString().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AnnotationReference " + getDataType() + " referring to " + reference;
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);
        if (comp == 0) {
            //types are equal, this must be of this type
            AnnotationReference value = (AnnotationReference) fieldValue;
            if (reference == null) {
                comp = (value.reference == null) ? 0 : -1;
            } else {
                comp = (value.reference == null) ? 1 : (reference.toString().compareTo(value.reference.toString()));
            }
        }
        return comp;
    }

}
