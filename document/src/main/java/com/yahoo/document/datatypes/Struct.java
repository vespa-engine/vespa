// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.collections.Hashlet;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.vespa.objects.Ids;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;


/**
 * @author Håkon Humberset
 */
public class Struct extends StructuredFieldValue {

    public static final int classId = registerClass(Ids.document + 33, Struct.class);
    private Hashlet<Integer, FieldValue> values = new Hashlet<>();
    private int [] order = null;

    private int version;

    private int [] getInOrder() {
        if (order == null) {
            order = new int[values.size()];
            for (int i = 0; i < values.size(); i++) {
                order[i] = values.key(i);
            }
            Arrays.sort(order);
        }
        return order;
    }

    private void invalidateOrder() {
        order = null;
    }

    public Struct(DataType type) {
        super((StructDataType) type);
        this.version = Document.SERIALIZED_VERSION;
    }

    @Override
    public StructDataType getDataType() {
        return (StructDataType)super.getDataType();
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return this.version;
    }

    @Override
    public Struct clone() {
        Struct struct = (Struct) super.clone();
        struct.values = new Hashlet<>();
        struct.values.reserve(values.size());
        for (int i = 0; i < values.size(); i++) {
            struct.values.put(values.key(i), values.value(i).clone());
        }
        return struct;
    }

    @Override
    public void clear() {
        values = new Hashlet<>();
        invalidateOrder();
    }

    @Override
    public Iterator<Map.Entry<Field, FieldValue>> iterator() {
        return new FieldSet().iterator();
    }

    public Set<Map.Entry<Field, FieldValue>> getFields() {
        return new FieldSet();
    }

    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        if (getDataType().equals(PositionDataType.INSTANCE)) {
            try {
                PositionDataType.renderXml(this, xml);
                return;
            } catch (RuntimeException e) {
                // fallthrough to handling below
            }
        }
        XmlSerializationHelper.printStructXml(this, xml);
    }

    @Override
    public FieldValue getFieldValue(Field field) {
        return values.get(field.getId());
    }


    @Override
    public Field getField(String fieldName) {
        return getDataType().getField(fieldName);
    }

    @Override
    public int getFieldCount() {
        return values.size();
    }

    @Override
    protected void doSetFieldValue(Field field, FieldValue value) {
        if (field == null) {
            throw new IllegalArgumentException("Invalid null field pointer");
        }
        Field myField = getDataType().getField(field.getId());
        if (myField==null) {
            throw new IllegalArgumentException("No such field in "+getDataType()+" : "+field.getName());
        }
        if (!myField
                   .getDataType().isValueCompatible(value)) {
            throw new IllegalArgumentException(
                    "Incompatible data types. Got " + value.getDataType()
                    + ", expected "
                    + myField.getDataType());
        }

        if (myField.getId()
                != field.getId()) {
            throw new IllegalArgumentException(
                    "Inconsistent field: " + field);
        }

        int index = values.getIndexOfKey(field.getId());
        if (index == -1) {
            values.put(field.getId(), value);
            invalidateOrder();
        } else {
            values.setValue(index, value);
        }
    }

    @Override
    public FieldValue removeFieldValue(Field field) {
        FieldValue found = values.get(field.getId());
        if (found != null) {
            Hashlet<Integer, FieldValue> copy = new Hashlet<>();
            copy.reserve(values.size() - 1);
            for (int i=0; i < values.size(); i++) {
                if (values.key(i) != field.getId()) {
                    copy.put(values.key(i), values.value(i));
                }
            }
            values = copy;
            invalidateOrder();
        }
        return found;
    }

    @Override
    public void assign(Object o) {
        if ((o instanceof Struct) && ((Struct) o).getDataType().equals(getDataType())) {
            clear();
            Iterator<Map.Entry<Field,FieldValue>> otherValues = ((Struct) o).iterator();
            while (otherValues.hasNext()) {
                Map.Entry<Field, FieldValue> otherEntry = otherValues.next();
                setFieldValue(otherEntry.getKey(), otherEntry.getValue());
            }
        } else {
            throw new IllegalArgumentException("Type " + o.getClass() + " can not specify a " + getClass() + " instance");
        }
    }

    /**
     * Clears this and assigns from the given {@link StructuredFieldValue}
     */
    public void assignFrom(StructuredFieldValue sfv) {
        clear();
        Iterator<Map.Entry<Field,FieldValue>> otherValues = sfv.iterator();
        while (otherValues.hasNext()) {
            Map.Entry<Field, FieldValue> otherEntry = otherValues.next();
            setFieldValue(otherEntry.getKey(), otherEntry.getValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Struct)) return false;
        if (!super.equals(o)) return false;

        Struct struct = (Struct) o;
        return values.equals(struct.values);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + values.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder retVal = new StringBuilder();
        retVal.append("Struct (").append(getDataType()).append("): ");
        int [] increasing = getInOrder();
        for (int i = 0; i < increasing.length; i++) {
            int id = increasing[i];
            retVal.append(getDataType().getField(id)).append("=").append(values.get(id)).append(", ");
        }
        return retVal.toString();
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    @Override
    public int compareTo(FieldValue obj) {
        int cmp = super.compareTo(obj);
        if (cmp != 0) {
            return cmp;
        }
        Struct rhs = (Struct)obj;
        cmp = values.size() - rhs.values.size();
        if (cmp != 0) {
            return cmp;
        }
        StructDataType type = getDataType();
        for (Field field : type.getFields()) {
            FieldValue lhsField = getFieldValue(field);
            FieldValue rhsField = rhs.getFieldValue(field);
            if (lhsField != null && rhsField != null) {
                cmp = lhsField.compareTo(rhsField);
                if (cmp != 0) {
                    return cmp;
                }
            } else if (lhsField != null || rhsField != null) {
                return (lhsField != null ? -1 : 1);
            }
        }
        return 0;
    }

    /*
     * (non-Javadoc)
     * @see com.yahoo.document.datatypes.FieldValue#deserialize(com.yahoo.document.Field, com.yahoo.document.serialization.FieldReader)
     */
    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    private class FieldEntry implements Map.Entry<Field, FieldValue> {
        private int id;

        private FieldEntry(int id) {
            this.id = id;
        }

        public Field getKey() {
            return getDataType().getField(id);
        }

        public FieldValue getValue() {
            return values.get(id);
        }

        public FieldValue setValue(FieldValue value) {
            if (value == null) {
                throw new NullPointerException("Null values in Struct not supported, use removeFieldValue() to remove value instead.");
            }

            int index = values.getIndexOfKey(id);
            FieldValue retVal = null;
            if (index == -1) {
                values.put(id, value);
                invalidateOrder();
            } else {
                retVal = values.value(index);
                values.setValue(index, value);
            }

            return retVal;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldEntry)) return false;

            FieldEntry that = (FieldEntry) o;
            return (id == that.id);
        }

        public int hashCode() {
            return id;
        }
    }

    private class FieldSet extends AbstractSet<Map.Entry<Field, FieldValue>> {
        @Override
        public int size() {
            return values.size();
        }

        @Override
        public Iterator<Map.Entry<Field, FieldValue>> iterator() {
            return new FieldSetIterator();
        }


    }

    private class FieldSetIterator implements Iterator<Map.Entry<Field, FieldValue>> {
        private int position = 0;
        private int [] increasing = getInOrder();

        public boolean hasNext() {
            return (position < increasing.length);
        }

        public Map.Entry<Field, FieldValue> next() {
            if (position >= increasing.length) {
                throw new NoSuchElementException("No more elements in collection");
            }
            FieldEntry retval = new FieldEntry(increasing[position]);
            position++;
            return retval;
        }

        public void remove() {
            throw new UnsupportedOperationException("The set of fields and values of this struct is unmodifiable when accessed through this method.");
        }
    }

    public static <T> T getFieldValue(FieldValue struct, DataType structType, String fieldName, Class<T> fieldType) {
        if (!(struct instanceof Struct)) {
            return null;
        }
        if (!struct.getDataType().equals(structType)) {
            return null;
        }
        FieldValue fieldValue = ((Struct)struct).getFieldValue(fieldName);
        if (!fieldType.isInstance(fieldValue)) {
            return null;
        }
        return fieldType.cast(fieldValue);
    }

    public static <T> T getFieldValue(FieldValue struct, DataType structType, Field field, Class<T> fieldType) {
        if (!(struct instanceof Struct)) {
            return null;
        }
        if (!struct.getDataType().equals(structType)) {
            return null;
        }
        FieldValue fieldValue = ((Struct)struct).getFieldValue(field);
        if (!fieldType.isInstance(fieldValue)) {
            return null;
        }
        return fieldType.cast(fieldValue);
    }

}
