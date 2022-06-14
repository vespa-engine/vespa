// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.objects.Ids;
import com.yahoo.vespa.objects.ObjectVisitor;

/**
 * @author Einar M R Rosenvinge
 */
public class WeightedSetDataType extends CollectionDataType {

    // The global class identifier shared with C++.
    public static int classId = registerClass(Ids.document + 55, WeightedSetDataType.class);

    /** Should an operation to a non-existent member of a weightedset cause the member to be created */
    private boolean createIfNonExistent = false;

    /** Should a member of a weightedset with weight 0 be removed */
    private boolean removeIfZero = false;

    /** The tag type is ambiguous, this flag is true if the user explicitly set a field to tag */
    private boolean tag = false;

    public WeightedSetDataType(DataType nestedType, boolean createIfNonExistent, boolean removeIfZero) {
        this(nestedType, createIfNonExistent, removeIfZero, 0);
    }

    public WeightedSetDataType(DataType nestedType, boolean createIfNonExistent, boolean removeIfZero, int id) {
        super(createName(nestedType, createIfNonExistent, removeIfZero), id, nestedType);
        this.createIfNonExistent = createIfNonExistent;
        this.removeIfZero = removeIfZero;
        if (id == 0) {
            if ((nestedType == STRING) && createIfNonExistent && removeIfZero) { // the tag type definition
                setId(TAG_ID);
            } else {
                setId(getName().toLowerCase().hashCode());
            }
        }
        int code = getId();
        if ((code >= 0) && (code <= DataType.lastPredefinedDataTypeId()) && (code != TAG_ID)) {
            throw new IllegalArgumentException("Cannot create a weighted set datatype with code " + code);
        }
    }

    @Override
    public WeightedSetDataType clone() {
        return (WeightedSetDataType) super.clone();
    }

    /**
     * Called by SD parser if a data type is explicitly tag.
     * 
     * @param tag true if this is a tag set.
     */
    public void setTag(boolean tag) {
        this.tag = tag;
    }

    /**
     * Returns whether or not this is a <em>tag</em> type weighted set.
     * 
     * @return true if this is a tag set.
     */
    public boolean isTag() {
        return tag;
    }

    static private String createName(DataType nested, boolean createIfNonExistent, boolean removeIfZero) {
        if (nested == DataType.STRING && createIfNonExistent && removeIfZero) {
            return "tag";
        } else {
            String name = "WeightedSet<" + nested.getName() + ">";
            if (createIfNonExistent) name += ";Add";
            if (removeIfZero) name += ";Remove";
            return name;
        }
    }

    @Override
    public WeightedSet createFieldValue() {
        return new WeightedSet(this);
    }

    @Override
    public Class getValueClass() {
        return WeightedSet.class;
    }

    /**
     * Returns true if this has the property createIfNonExistent (only relevant for weighted sets)
     *
     * @return createIfNonExistent property
     */
    public boolean createIfNonExistent() {
        return createIfNonExistent;
    }

    /**
     * Returns true if this has the property removeIfZero (only relevant for weighted sets)
     *
     * @return removeIfZero property
     */
    public boolean removeIfZero() {
        return removeIfZero;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("removeIfZero", removeIfZero);
        visitor.visit("createIfNonExistent", createIfNonExistent);
    }

    @Override
    public FieldPath buildFieldPath(String remainFieldName) {
        return MapDataType.buildFieldPath(remainFieldName, getNestedType(), DataType.INT);
    }

}
