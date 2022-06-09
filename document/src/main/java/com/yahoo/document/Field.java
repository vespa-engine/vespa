// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.collections.BobHash;
import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.document.fieldset.NoFields;
import com.yahoo.vespa.objects.FieldBase;

/**
 * A name and type. Fields are contained in document types to describe their fields,
 * but is also used to represent name/type pairs which are not part of document types.
 *
 * @author Thomas Gundersen
 * @author bratseth
 */
public class Field extends FieldBase implements FieldSet, Comparable<Field> {

    protected DataType dataType;
    protected int fieldId;
    private boolean forcedId;

    /**
     * Creates a new field.
     *
     * @param name     The name of the field
     * @param id       Serialized ID
     * @param dataType The datatype of the field
     */
    public Field(String name, int id, DataType dataType) {
        super(name);
        this.fieldId = id;
        this.dataType = dataType;
        this.forcedId = true;
        validateId(id, null);
    }

    public Field(String name) {
        this(name, DataType.NONE);
    }


    /**
     * Creates a new field.
     *
     * @param name     The name of the field
     * @param dataType The datatype of the field
     * @param owner    the owning document (used to check for id collisions)
     */
    public Field(String name, DataType dataType, DocumentType owner) {
        this(name, 0, dataType);
        this.fieldId = calculateIdV7(owner);
        this.forcedId = false;
    }

    /**
     * Constructor for normal fields
     *
     * @param name     The name of the field
     * @param dataType The datatype of the field
     */
    public Field(String name, DataType dataType) {
        this(name, dataType, null);
    }

    /**
     * Creates a field with a new name and the other properties
     * (excluding the id and owner) copied from another field
     */
    // TODO: Decide on one copy/clone idiom and do it for this and all it is calling
    public Field(String name, Field field) {
        this(name, field.dataType, null);
    }

    public int compareTo(Field o) {
        return fieldId - o.fieldId;
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
        validateId(newId, owner);
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

        validateId(newId, owner);

        owner.removeField(getName());
        this.fieldId = newId;
        this.forcedId = true;
        owner.addField(this);
    }

    private void validateId(int newId, DocumentType owner) {
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
            Field existing = owner.getField(newId);
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
    @Deprecated // TODO: Refactor SD processing to avoid needing this
    public void setDataType(DataType type) {
        dataType = type;
        fieldId = calculateIdV7(null);
        forcedId = false;
    }

    public final int getId() {
        return fieldId;
    }

    /**
     *
     * @return true if the field has a forced id
     */
    public final boolean hasForcedId() {
        return forcedId;
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

    @Override
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
