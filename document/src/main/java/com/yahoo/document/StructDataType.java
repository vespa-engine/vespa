// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.vespa.objects.Ids;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public class StructDataType extends BaseStructDataType {

    public static final int classId = registerClass(Ids.document + 57, StructDataType.class);
    private StructDataType superType = null;

    public StructDataType(String name) {
        super(name);
    }

    public StructDataType(int id, String name) {
        super(id, name);
    }

    @Override
    public Struct createFieldValue() {
        return new Struct(this);
    }

    @Override
    public FieldValue createFieldValue(Object o) {
        Struct struct;
        if (o.getClass().equals(Struct.class)) {
            struct = new Struct(this);
        } else {
            // This indicates for example that o is a generated struct subtype, try the empty constructor
            try {
                struct = (Struct) o.getClass().getConstructor().newInstance();
            } catch (Exception e) {
                // Fallback, let assign handle the error if o is completely bogus
                struct = new Struct(this);
            }
        }
        struct.assign(o);
        return struct;
    }

    @Override
    public StructDataType clone() {
        StructDataType type = (StructDataType) super.clone();
        type.superType = this.superType;
        return type;
    }

    public void assign(StructDataType type) {
        super.assign(type);
        superType = type.superType;
    }

    @Override
    public Field getField(String fieldName) {
        Field f = super.getField(fieldName);
        if (f == null && superType != null) {
            f = superType.getField(fieldName);
        }
        return f;
    }

    @Override
    public Field getField(int id) {
        Field f = super.getField(id);
        if (f == null && superType != null) {
            f = superType.getField(id);
        }
        return f;
    }

    @Override
    public void addField(Field field) {
        if (hasField(field)) {
            throw new IllegalArgumentException("Struct already has field " + field);
        }
        if ((superType != null) && superType.hasField(field)) {
            throw new IllegalArgumentException(field.toString() + " already present in inherited type '" + superType.toString() + "', " + this.toString() + " cannot override.");
        }
        super.addField(field);
    }

    @Override
    public Collection<Field> getFields() {
        if (superType == null) {
            return Collections.unmodifiableCollection(super.getFields());
        }
        Collection<Field> fieldsBuilder = new ArrayList<>();
        fieldsBuilder.addAll(super.getFields());
        fieldsBuilder.addAll(superType.getFields());
        return List.copyOf(fieldsBuilder);
    }

    public Collection<Field> getFieldsThisTypeOnly() {
        return Collections.unmodifiableCollection(super.getFields());
    }

    @Override
    public int getFieldCount() {
        return getFields().size();
    }

    @Override
    public Class getValueClass() {
        return Struct.class;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        if (!(value instanceof Struct structValue)) {
            return false;
        }
        if (structValue.getDataType().inherits(this)) {
            //the value is of this type; or the supertype of the value is of this type, etc....
            return true;
        }
        return false;
    }

    public void inherit(StructDataType type) {
        if (superType != null) {
            throw new IllegalArgumentException("Already inherits type " + superType + ", multiple inheritance not currently supported.");
        }
        for (Field f : type.getFields()) {
            if (hasField(f)) {
                throw new IllegalArgumentException(f + " already present in " + type + ", " + this + " cannot inherit from it");
            }
        }
        superType = type;
    }

    public Collection<StructDataType> getInheritedTypes() {
        if (superType == null) {
            return List.of();
        }
        return List.of(superType);
    }

    public boolean inherits(StructDataType type) {
        if (equals(type)) return true;
        if (superType != null && superType.inherits(type)) return true;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructDataType that)) return false;
        if (!super.equals(o)) return false;

        if (superType != null ? !superType.equals(that.superType) : that.superType != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (superType != null ? superType.hashCode() : 0);
        return result;
    }
}
