// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.collections.BobHash;
import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.document.fieldset.NoFields;
import com.yahoo.vespa.objects.FieldBase;

import java.io.Serializable;

/**
 * A name and type. Fields are contained in document types to describe their fields,
 * but is also used to represent name/type pairs which are not part of document types.
 *
 * @author Thomas Gundersen
 * @author bratseth
 */
// TODO: Remove header/body concept on Vespa 8
public class Field extends FieldBase implements FieldSet, Comparable, Serializable {

    protected DataType dataType;
    protected int fieldId;
    private int fieldIdV6;
    private boolean isHeader;
    private boolean forcedId = false;

    /**
     * Creates a new field.
     *
     * @param name     The name of the field
     * @param dataType The datatype of the field
     * @param isHeader Whether this is a "header" field or a "content" field
     *                 (true = "header").
     */
    public Field(String name, int id, DataType dataType, boolean isHeader) {
        super(name);
        this.fieldId = id;
        this.fieldIdV6 = id;
        this.dataType = dataType;
        this.isHeader = isHeader;
        this.forcedId = true;
        validateId(id, null, Document.SERIALIZED_VERSION);
    }

    public Field(String name) {
        this(name, DataType.NONE);
    }


    /**
     * Creates a new field.
     *
     * @param name     The name of the field
     * @param dataType The datatype of the field
     * @param isHeader Whether this is a "header" field or a "content" field
     *                 (true = "header").
     * @param owner    the owning document (used to check for id collisions)
     */
    public Field(String name, DataType dataType, boolean isHeader, DocumentType owner) {
        this(name, 0, dataType, isHeader);
        this.fieldId = calculateIdV7(owner);
        this.fieldIdV6 = calculateIdV6(owner);
        this.forcedId = false;
    }

    /**
     * Creates a new field.
     *
     * @param name     The name of the field
     * @param dataType The datatype of the field
     * @param isHeader Whether this is a "header" field or a "content" field
     *                 (true = "header").
     */
    public Field(String name, DataType dataType, boolean isHeader) {
        this(name, dataType, isHeader, null);
    }

    /**
     * Constructor for <b>header</b> fields
     *
     * @param name     The name of the field
     * @param dataType The datatype of the field
     */
    public Field(String name, DataType dataType) {
        this(name, dataType, true);
    }

    /**
     * Creates a field with a new name and the other properties
     * (excluding the id and owner) copied from another field
     */
    // TODO: Decide on one copy/clone idiom and do it for this and all it is calling
    public Field(String name, Field field) {
        this(name, field.dataType, field.isHeader, null);
    }

    /**
     * The field id must be unique within a document type, and also
     * within a (unknown at this time) hierarchy of document types.
     * In addition it should be as resilient to doctype content changes
     * and inheritance hierarchy changes as possible.
     * All of this is enforced for names, so id's should follow names.
     * Therefore we hash on name.
     */
    private int calculateIdV6(DocumentType owner) {
        int newId = BobHash.hash(getName()); // Using a portfriendly hash
        if (newId < 0) newId = -newId; // Highest bit is reserved to tell 7-bit id's from 31-bit ones
        validateId(newId, owner, 6);
        return newId;
    }

    public int compareTo(Object o) {
        return fieldId - ((Field) o).fieldId;
    }

    /**
     * The field id must be unique within a document type, and also
     * within a (unknown at this time) hierarchy of document types.
     * In addition it should be as resilient to doctype content changes
     * and inheritance hierarchy changes as possible.
     * All of this is enforced for names, so id's should follow names.
     * Therefore we hash on name.
     */
    protected int calculateIdV7(DocumentType owner) {
        String combined = getName() + dataType.getId();

        int newId = BobHash.hash(combined); // Using a portfriendly hash
        if (newId < 0) newId = -newId; // Highest bit is reserved to tell 7-bit id's from 31-bit ones
        validateId(newId, owner, Document.SERIALIZED_VERSION);
        return newId;
    }

    /**
     * Sets the id of this field. Don't do this unless you know what you are doing
     *
     * @param newId the id - if this is less than 100 it will cause document to serialize
     *              using just one byte for this field id. 100-127 are reserved values
     * @param owner the owning document, this is checked for collisions and notified
     *              of the id change. It can not be null
     */
    public void setId(int newId, DocumentType owner) {
        if (owner == null) {
            throw new NullPointerException("Can not assign an id of " + this + " without knowing the owner");
        }

        validateId(newId, owner, Document.SERIALIZED_VERSION);

        owner.removeField(getName());
        this.fieldId = newId;
        this.fieldIdV6 = newId;
        this.forcedId = true;
        owner.addField(this);
    }

    private void validateId(int newId, DocumentType owner, int version) {
        if (newId >= 100 && newId <= 127) {
            throw new IllegalArgumentException("Attempt to set the id of " + this + " to " + newId +
                                               " failed, values from 100 to 127 " + "are reserved for internal use");
        }

        if ((newId & 0x80000000) != 0) // Highest bit must not be set
        {
            throw new IllegalArgumentException("Attempt to set the id of " + this + " to " + newId +
                                               " failed, negative id values " + " are illegal");
        }


        if (owner == null) return;
        {
            Field existing = owner.getField(newId, version);
            if (existing != null && !existing.getName().equals(getName())) {
                throw new IllegalArgumentException("Couldn't set id of " + this + " to " + newId + ", " + existing +
                                                   " already has this id in " + owner);
            }
        }
    }

    /** Returns the datatype of the field */
    public final DataType getDataType() {
        return dataType;
    }

    /**
     * Set the data type of the field. This will cause recalculation of fieldid for version 7+.
     *
     * @param type The new type of the field.
     * @deprecated do not use
     */
    @Deprecated // todo - refactor SD processing to avoid needing this
    public void setDataType(DataType type) {
        dataType = type;
        fieldId = calculateIdV7(null);
        forcedId = false;
    }

    /** Returns the numeric ID used to represent this field when serialized */
    public final int getId(int version) {
        return (version > 6) ? getId() : getIdV6();
    }

    public final int getId() {
        return fieldId;
    }

    public final int getIdV6() {
        return fieldIdV6;
    }

    /**
     *
     * @return true if the field has a forced id
     */
    public final boolean hasForcedId() {
        return forcedId;
    }

    /** @deprecated this has no longer any semantic meaning as this is no longer an aspect with a field */
    @Deprecated // TODO: Remove on Vespa 8
    public boolean isHeader() {
        return isHeader;
    }

    /** @deprecated this has no longer any semantic meaning as this is no longer an aspect with a field */
    @Deprecated // TODO: Remove on Vespa 8
    public void setHeader(boolean header) {
        this.isHeader = header;
    }

    /** Two fields are equal if they have the same name and the same data type */
    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Field && super.equals(o) && dataType.equals(((Field) o).dataType);
    }

    @Override
    public int hashCode() {
        return getId();
    }

    public String toString() {
        return super.toString() + "(" + dataType + ")";
    }

    @Override
    public boolean contains(FieldSet o) {
        if (o instanceof NoFields || o instanceof DocIdOnly) {
            return true;
        }

        if (o instanceof Field) {
            return equals(o);
        }

        return false;
    }

    @Override
    public FieldSet clone() throws CloneNotSupportedException {
        return (Field)super.clone();
    }
}
