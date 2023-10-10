// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.vespa.objects.Ids;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Håkon Humberset
 */
public abstract class StructuredDataType extends DataType {

    public static final int classId = registerClass(Ids.document + 56, StructuredDataType.class);

    protected static int createId(String name) {
        if (name.equals("document")) return 8;

        // This is broken really because we now depend on String.hashCode staying the same in Java vm's
        // which is likely for pragmatic reasons but not by contract
        return (name+".0").hashCode(); // the ".0" must be preserved to keep hashCodes the same after we removed version
    }

    public StructuredDataType(String name) {
        super(name, createId(name));
    }

    public StructuredDataType(int id, String name) {
        super(name, id);
    }

    @Override
    public abstract StructuredFieldValue createFieldValue();

    @Override
    protected FieldValue createByReflection(Object arg) { return null; }

    /**
     * Returns the name of this as a DataTypeName
     *
     * @return Return the Documentname of this doumenttype.
     */
    public DataTypeName getDataTypeName() {
        return new DataTypeName(getName());
    }

    /**
     * Gets the field  matching a given name.
     *
     * @param name The name of a field.
     * @return Returns the matching field, or null if not found.
     */
    public abstract Field getField(String name);

    /**
     * Gets the field with the specified id.
     *
     * @param id the id of the field to return.
     * @return the matching field, or null if not found.
     */
    public abstract Field getField(int id);

    public abstract Collection<Field> getFields();

    @Override
    public boolean equals(Object o) {
        return ((o instanceof StructuredDataType) && super.equals(o));
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    protected void register(DocumentTypeManager manager, List<DataType> seenTypes) {
        seenTypes.add(this);
        for (Field field : getFields()) {
            if (!seenTypes.contains(field.getDataType())) {
                //we haven't seen this one before, register it:
                field.getDataType().register(manager, seenTypes);
            }
        }
        super.register(manager, seenTypes);
    }

    @Override
    public FieldPath buildFieldPath(String remainFieldName) {
        if (remainFieldName.length() == 0) {
            return new FieldPath();
        }

        String currFieldName = remainFieldName;
        String subFieldName = "";

        for (int i = 0; i < remainFieldName.length(); i++) {
            if (remainFieldName.charAt(i) == '.') {
                currFieldName = remainFieldName.substring(0, i);
                subFieldName = remainFieldName.substring(i + 1);
                break;
            } else if (remainFieldName.charAt(i) == '{' || remainFieldName.charAt(i) == '[') {
                currFieldName = remainFieldName.substring(0, i);
                subFieldName = remainFieldName.substring(i);
                break;
            }
        }

        Field f = getField(currFieldName);
        if (f != null) {
            FieldPath fieldPath = f.getDataType().buildFieldPath(subFieldName);
            List<FieldPathEntry> tmpPath = new ArrayList<FieldPathEntry>(fieldPath.getList());
            tmpPath.add(0, FieldPathEntry.newStructFieldEntry(f));
            return new FieldPath(tmpPath);
        } else {
            throw new IllegalArgumentException("Field '" + currFieldName + "' not found in type " + this);
        }
    }

}
