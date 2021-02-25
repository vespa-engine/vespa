// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.google.common.collect.ImmutableList;
import com.yahoo.collections.MD5;
import com.yahoo.document.DataType;

import java.util.Collection;
import java.util.Collections;

/**
 * An AnnotationType describes a certain type of annotations; they are
 * generally distinguished by a name, an id, and an optional data type.
 * <p>
 * If an AnnotationType has a {@link com.yahoo.document.DataType}, this means that {@link Annotation}s of
 * that type are allowed to have a {@link com.yahoo.document.datatypes.FieldValue} of the given
 * {@link com.yahoo.document.DataType} as an optional payload.
 *
 * @author Einar M R Rosenvinge
 */
public class AnnotationType implements Comparable<AnnotationType> {

    private final int id;
    private final String name;
    private DataType dataType;
    private AnnotationType superType = null;

    /**
     * Creates a new annotation type that cannot have values (hence no data type).
     *
     * @param name the name of the new annotation type
     */
    public AnnotationType(String name) {
        this(name, null);
    }

    /**
     * Creates a new annotation type that can have values of the specified type.
     *
     * @param name     the name of the new annotation type
     * @param dataType the data type of the annotation value
     */
    public AnnotationType(String name, DataType dataType) {
        this.name = name;
        this.dataType = dataType;
        //always keep this as last statement in here:
        this.id = computeHash();
    }

    /**
     * Creates a new annotation type that can have values of the specified type.
     *
     * @param name     the name of the new annotation type
     * @param dataType the data type of the annotation value
     * @param id   the ID of the new annotation type
     */
    public AnnotationType(String name, DataType dataType, int id) {
        this.name = name;
        this.dataType = dataType;
        this.id = id;
    }

    /**
     * Creates a new annotation type, with the specified ID. WARNING! Only to be used by configuration
     * system, do not use!!
     *
     * @param name the name of the new annotation type
     * @param id   the ID of the new annotation type
     */
    public AnnotationType(String name, int id) {
        this.id = id;
        this.name = name;
    }

    /** Returns the name of this annotation. */
    public String getName() {
        return name;
    }

    /** Returns the data type of this annotation, if any. */
    public DataType getDataType() {
        return dataType;
    }

    /**
     * Sets the data type of this annotation. WARNING! Only to be used by configuration
     * system, do not use!!
     *
     * @param dataType the data type of the annotation value
     */
    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    /** Returns the ID of this annotation. */
    public int getId() {
        return id;
    }

    private int computeHash() {
        return new MD5().hash(name);
    }

    public boolean isValueCompatible(Annotation structValue) {
        if (structValue.getType().inherits(this)) {
            //the value is of this type; or the supertype of the value is of this type, etc....
            return true;
        }
        return false;
    }

    /**
     * WARNING! Only to be used by the configuration system and in unit tests. Not to be used in production code.
     *
     * @param type the type to inherit from
     */
    public void inherit(AnnotationType type) {
        if (superType != null) {
            throw new IllegalArgumentException("Already inherits type " + superType +
                                               ", multiple inheritance not currently supported.");
        }
        superType = type;
    }

    public Collection<AnnotationType> getInheritedTypes() {
        if (superType == null) {
            return ImmutableList.of();
        }
        return ImmutableList.of(superType);
    }

    public boolean inherits(AnnotationType type) {
        if (equals(type)) return true;
        if (superType != null && superType.inherits(type)) return true;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnotationType)) return false;

        AnnotationType that = (AnnotationType) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder();
        strb.append(name).append(" (id ").append(id);
        if (dataType != null) {
            strb.append(", data type ").append(dataType);
        }
        strb.append(")");
        return strb.toString();
    }

    @Override
    public int compareTo(AnnotationType annotationType) {
        if (annotationType == null) {
            return 1;
        }
        if (id < annotationType.id) {
            return -1;
        } else if (id > annotationType.id) {
            return 1;
        }
        return 0;
    }

}
