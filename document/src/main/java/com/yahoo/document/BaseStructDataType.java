// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Date: Apr 15, 2008
 *
 * @author humbe
 */
public abstract class BaseStructDataType extends StructuredDataType {

    protected Map<Integer, Field> fieldIds = new LinkedHashMap<>();
    protected Map<String, Field> fields = new LinkedHashMap<>();

    BaseStructDataType(String name) {
        super(name);
    }

    BaseStructDataType(int id, String name) {
        super(id, name);
    }

    protected void assign(BaseStructDataType type) {
        BaseStructDataType stype = type.clone();

        fieldIds = stype.fieldIds;
        fields = stype.fields;
    }

    @Override
    public BaseStructDataType clone() {
        BaseStructDataType type = (BaseStructDataType) super.clone();
        type.fieldIds = new LinkedHashMap<>();

        type.fields = new LinkedHashMap<>();
        for (Field field : fieldIds.values()) {
            type.fields.put(field.getName(), field);
            type.fieldIds.put(field.getId(), field);
        }
        return type;
    }

    public void addField(Field field) {
        if (fields.containsKey(field.getName())) {
            throw new IllegalArgumentException("Struct " + getName() + " already contains field with name " + field.getName());
        }
        if (fieldIds.containsKey(field.getId())) {
            throw new IllegalArgumentException("Struct " + getName() + " already contains field with id " + field.getId());
        }

        fields.put(field.getName(), field);
        fieldIds.put(field.getId(), field);
    }

    public Field removeField(String fieldName) {
        Field old = fields.remove(fieldName);
        if (old != null) {
            fieldIds.remove(old.getId());
        }
        return old;
    }

    public void clearFields() {
        fieldIds.clear();
        fields.clear();
    }

    @Override
    public Field getField(String fieldName) {
        return fields.get(fieldName);
    }

    @Override
    public Field getField(int id) {
        return fieldIds.get(id);
    }

    public boolean hasField(Field field) {
        Field f = getField(field.getId());
        return f != null && f.equals(field);
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    @Override
    public Collection<Field> getFields() {
        return fields.values();
    }

    public int getFieldCount() {
        return fields.size();
    }

}
