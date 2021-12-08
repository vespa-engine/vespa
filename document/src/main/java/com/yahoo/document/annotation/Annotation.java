// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * An Annotation describes some kind of information associated with a {@link SpanNode}.
 *
 * @see com.yahoo.document.annotation.SpanNode
 * @see com.yahoo.document.annotation.AnnotationType
 * @author Einar M R Rosenvinge
 */
public class Annotation implements Comparable<Annotation> {

    private AnnotationType type;
    private SpanNode spanNode = null;
    private FieldValue value = null;

    /** This scratch id is used to avoid using IdentityHashMaps as they are very costly. */
    private int scratchId = -1;

    public void setScratchId(int id) {
        scratchId = id;
    }

    public int getScratchId() {
        return scratchId;
    }


    /**
     * Constructs an Annotation without a type. BEWARE! Should only be used during deserialization.
     */
    public Annotation() {
    }

    /**
     * Constructs a new annotation of the specified type.
     *
     * @param type the type of the new annotation
     */
    public Annotation(AnnotationType type) {
        this.type = type;
    }

    /**
     * Constructs a copy of the input annotation.
     *
     * @param other annotation
     */
    public Annotation(Annotation other) {
        this.type = other.type;
        this.value = ((other.value == null) ? null : other.value.clone());
        //do not copy spanNode now
    }

    /**
     * Constructs a new annotation of the specified type, and having the specified value.
     *
     * @param type  the type of the new annotation
     * @param value the value of the new annotation
     * @throws UnsupportedOperationException if the annotation type does not allow this annotation to have values.
     */
    public Annotation(AnnotationType type, FieldValue value) {
        this(type);
        setFieldValue(value);
    }

    /**
     * Returns the type of this annotation.
     *
     * @return the type of this annotation
     */
    public AnnotationType getType() {
        return type;
    }

    /**
     * Sets the type of this annotation. BEWARE! Should only be used during deserialization.
     *
     * @param type the type of this annotation
     */
    public void setType(AnnotationType type) {
        this.type = type;
    }

    /**
     * Returns true if this Annotation is associated with a SpanNode (irrespective of the SpanNode being valid or not).
     *
     * @return true if this Annotation is associated with a SpanNode, false otherwise.
     * @see com.yahoo.document.annotation.SpanNode#isValid()
     */
    public boolean hasSpanNode() {
        return spanNode != null;
    }

    /**
     * Returns true iff this Annotation is associated with a SpanNode and the SpanNode is valid.
     *
     * @return true iff this Annotation is associated with a SpanNode and the SpanNode is valid.
     * @see com.yahoo.document.annotation.SpanNode#isValid()
     */
    public boolean isSpanNodeValid() {
        return spanNode != null && spanNode.isValid();
    }


    /**
     * Returns the {@link SpanNode} this Annotation is annotating, if any.
     *
     * @return the {@link SpanNode} this Annotation is annotating, or null
     * @throws IllegalStateException if this Annotation is associated with a SpanNode and the SpanNode is invalid.
     */
    public SpanNode getSpanNode() {
        if (spanNode != null && !spanNode.isValid()) {
            throw new IllegalStateException("Span node is invalid: " + spanNode);
        }
        return spanNode;
    }

    /**
     * Returns the {@link SpanNode} this Annotation is annotating, if any.
     *
     * @return the {@link SpanNode} this Annotation is annotating, or null
     */
    public final SpanNode getSpanNodeFast() {
        return spanNode;
    }

    /**
     * WARNING! Should only be used by deserializers!&nbsp;Sets the span node that this annotation points to.
     *
     * @param spanNode the span node that this annotation shall point to.
     */
    public void setSpanNode(SpanNode spanNode) {
        if (this.spanNode != null && spanNode != null) {
            throw new IllegalStateException("WARNING! " + this + " is already attached to node " + this.spanNode
                                            + ". Attempt to attach to node " + spanNode
                                            + ". Annotation instances MUST NOT be shared among SpanNodes.");
        }
        if (spanNode != null && !spanNode.isValid()) {
            throw new IllegalStateException("Span node is invalid: " + spanNode);
        }
        if (spanNode == DummySpanNode.INSTANCE) {
            // internal safeguard
            throw new IllegalStateException("BUG! Annotations should never be attached to DummySpanNode.");
        }
        this.spanNode = spanNode;
    }

    /**
     * WARNING! Should only be used by deserializers! Sets the span node that this annotation points to.
     *
     * @param spanNode the span node that this annotation shall point to.
     */
    public final void setSpanNodeFast(SpanNode spanNode) {
        this.spanNode = spanNode;
    }

    /**
     * Returns the value of the annotation, if any.
     *
     * @return the value of the annotation, or null
     */
    public FieldValue getFieldValue() {
        return value;
    }

    /**
     * Sets the value of the annotation.
     *
     * @param fieldValue the value to set
     * @throws UnsupportedOperationException if the annotation type does not allow this annotation to have values.
     */
    public void setFieldValue(FieldValue fieldValue) {
        if (fieldValue == null) {
            value = null;
            return;
        }

        DataType type = getType().getDataType();
        if (type != null && type.isValueCompatible(fieldValue)) {
            this.value = fieldValue;
        } else {
            String typeName = (type == null) ? "null" : type.getValueClass().getName();
            throw new IllegalArgumentException("Argument is of wrong type, must be of type " + typeName
                                               + ", was " + fieldValue.getClass().getName());
        }
    }

    /**
     * Returns true if this Annotation has a value.
     *
     * @return true if this Annotation has a value, false otherwise.
     */
    public boolean hasFieldValue() {
        return value != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Annotation)) return false;

        Annotation that = (Annotation) o;
        if (!type.equals(that.type)) return false;
        if (spanNode != null ? !spanNode.equals(that.spanNode) : that.spanNode != null) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (spanNode != null ? spanNode.hashCode() : 0);
        result = 31 * result + (value != null ? value.toString().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        String retval = "annotation of type " + type;
        retval += ((value == null) ? " (no value)" : " (with value)");
        retval += ((spanNode == null) ? " (no span)" : (" with span "+spanNode));
        return retval;
    }


    @Override
    public int compareTo(Annotation annotation) {
        int comp;

        if (spanNode == null) {
            comp = (annotation.spanNode == null) ? 0 : -1;
        } else {
            comp = (annotation.spanNode == null) ? 1 : spanNode.compareTo(annotation.spanNode);
        }

        if (comp != 0) {
            return comp;
        }

        comp = type.compareTo(annotation.type);

        if (comp != 0) {
            return comp;
        }

        // types are equal, too, compare values
        if (value == null) {
            comp = (annotation.value == null) ? 0 : -1;
        } else {
            comp = (annotation.value == null) ? 1 : value.compareTo(annotation.value);
        }

        return comp;
    }

}

