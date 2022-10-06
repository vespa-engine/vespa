// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.*;
import com.yahoo.vespa.objects.Ids;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Håkon Humberset
 */
public abstract class StructuredFieldValue extends CompositeFieldValue {

    public static final int classId = registerClass(Ids.document + 32, StructuredFieldValue.class);

    protected StructuredFieldValue(StructuredDataType type) {
        super(type);
    }

    @Override
    public StructuredDataType getDataType() {
        return (StructuredDataType)super.getDataType();
    }

    /**
     * Returns the named field object, or null if that field does not exist.
     *
     * @param fieldName The name of the field to return.
     * @return The corresponding field, or null.
     */
    public abstract Field getField(String fieldName);

    /**
     * Returns the value of the given field. If the field does not exist, this method returns null.
     *
     * @param field The field whose value to return.
     * @return The value of the field, or null.
     */
    public abstract FieldValue getFieldValue(Field field);

    /**
     * Convenience method to return the value of a named field. This is the same as calling {@link #getField(String)},
     * and using the returned value to call {@link #getFieldValue(Field)}. If the named field does not exist, this
     * method returns null.
     *
     * @param fieldName the name of the field whose value to return.
     * @return the value of the field, or null if it is not declared in this, or has no value set
     */
    public FieldValue getFieldValue(String fieldName) {
        Field field = getField(fieldName);
        if (field == null) return null;
        return getFieldValue(field);
    }

    /**
     * Sets the value of the given field. The type of the value must match the type of this field, i.e.
     * <pre>field.getDataType().getValueClass().isAssignableFrom(value.getClass())</pre> must be true.
     *
     * @param field the field whose value to set
     * @param value the value to set
     * @return the previous value of the field, or null
     * @throws IllegalArgumentException if the value is not compatible with the field
     */
    public FieldValue setFieldValue(Field field, FieldValue value) {
        if (value == null) {
            return removeFieldValue(field);
        }
        DataType type = field.getDataType();
        if (!type.getValueClass().isAssignableFrom(value.getClass())) {
            FieldValue tmp = type.createFieldValue();
            tmp.assign(value);
            value = tmp;
        }
        FieldValue ret = getFieldValue(field);
        doSetFieldValue(field, value);
        return ret;
    }

    protected abstract void doSetFieldValue(Field field, FieldValue value);

    /**
     * Convenience method to set the value of a named field. This is the same as calling {@link #getField(String)}, and
     * using the returned value to call {@link #setFieldValue(Field, FieldValue)}. If the named field does not exist,
     * this method returns null.
     *
     * @param fieldName The name of the field whose value to set.
     * @param value     The value to set.
     * @return The previous value of the field, or null.
     */
    public FieldValue setFieldValue(String fieldName, FieldValue value) {
        Field field = getField(fieldName);
        if (field == null) {
            return null;
        }
        return setFieldValue(field, value);
    }

    public final FieldValue setFieldValue(Field field, String value) {
        return setFieldValue(field, new StringFieldValue(value));
    }

    public final FieldValue setFieldValue(Field field, Double value) {
        return setFieldValue(field, new DoubleFieldValue(value));
    }

    public final FieldValue setFieldValue(Field field, Integer value) {
        return setFieldValue(field, new IntegerFieldValue(value));
    }

    public final FieldValue setFieldValue(Field field, Long value) {
        return setFieldValue(field, new LongFieldValue(value));
    }

    public final FieldValue setFieldValue(Field field, Byte value) {
        return setFieldValue(field, new ByteFieldValue(value));
    }

    public final FieldValue setFieldValue(String field, String value) {
        return setFieldValue(field, new StringFieldValue(value));
    }

    public final FieldValue setFieldValue(String field, Double value) {
        return setFieldValue(field, new DoubleFieldValue(value));
    }

    public final FieldValue setFieldValue(String field, Integer value) {
        return setFieldValue(field, new IntegerFieldValue(value));
    }

    public final FieldValue setFieldValue(String field, Long value) {
        return setFieldValue(field, new LongFieldValue(value));
    }

    public final FieldValue setFieldValue(String field, Byte value) {
        return setFieldValue(field, new ByteFieldValue(value));
    }

    public final FieldValue setFieldValue(String field, Boolean value) {
        return setFieldValue(field, new BoolFieldValue(value));
    }
    /**
     * Removes and returns a field value.
     *
     * @param field The field whose value to remove.
     * @return The previous value of the field, or null.
     */
    public abstract FieldValue removeFieldValue(Field field);

    /**
     * Convenience method to remove the value of a named field. This is the same as calling {@link #getField(String)},
     * and using the returned value to call {@link #removeFieldValue(Field)}. If the named field does not exist, this
     * method returns null.
     *
     * @param fieldName The name of the field whose value to remove.
     * @return The previous value of the field, or null.
     */
    public FieldValue removeFieldValue(String fieldName) {
        Field field = getField(fieldName);
        if (field == null) {
            return null;
        }
        return removeFieldValue(field);
    }

    public abstract void clear();

    public abstract int getFieldCount();

    public abstract Iterator<Map.Entry<Field, FieldValue>> iterator();

    @Override
    public FieldPathIteratorHandler.ModificationStatus iterateNested(FieldPath fieldPath, int pos,
                                                                     FieldPathIteratorHandler handler) {
        if (pos < fieldPath.size()) {
            if (fieldPath.get(pos).getType() == FieldPathEntry.Type.STRUCT_FIELD) {
                FieldValue fieldVal = getFieldValue(fieldPath.get(pos).getFieldRef());
                if (fieldVal != null) {
                    FieldPathIteratorHandler.ModificationStatus status = fieldVal.iterateNested(fieldPath, pos + 1, handler);
                    if (status == FieldPathIteratorHandler.ModificationStatus.REMOVED) {
                        removeFieldValue(fieldPath.get(pos).getFieldRef());
                        return FieldPathIteratorHandler.ModificationStatus.MODIFIED;
                    } else {
                        if (isGenerated()) {
                            // If this is a generated doc, the operations on the FieldValue in iterateNested do not write through to the doc,
                            // so set the field again here. Should be a cleaner way to do this.
                            setFieldValue(fieldPath.get(pos).getFieldRef(), fieldVal);
                        }
                        return status;
                    }
                } else if (handler.createMissingPath()) {
                    FieldValue newVal = fieldPath.get(pos).getFieldRef().getDataType().createFieldValue();
                    FieldPathIteratorHandler.ModificationStatus status = newVal.iterateNested(fieldPath, pos + 1, handler);
                    if (status == FieldPathIteratorHandler.ModificationStatus.MODIFIED) {
                        setFieldValue(fieldPath.get(pos).getFieldRef(), newVal);
                        return status;
                    }
                }
                return FieldPathIteratorHandler.ModificationStatus.NOT_MODIFIED;
            }
            throw new IllegalArgumentException("Illegal field path " + fieldPath.get(pos) + " for struct value");
        } else {
            FieldPathIteratorHandler.ModificationStatus status = handler.modify(this);
            if (status == FieldPathIteratorHandler.ModificationStatus.REMOVED) {
                return status;
            }
            if (handler.onComplex(this)) {
                List<Field> fieldsToRemove = new ArrayList<Field>();
                for (Iterator<Map.Entry<Field, FieldValue>> iter = iterator(); iter.hasNext();) {
                    Map.Entry<Field, FieldValue> entry = iter.next();
                    FieldPathIteratorHandler.ModificationStatus currStatus = entry.getValue().iterateNested(fieldPath, pos, handler);
                    if (currStatus == FieldPathIteratorHandler.ModificationStatus.REMOVED) {
                        fieldsToRemove.add(entry.getKey());
                        status = FieldPathIteratorHandler.ModificationStatus.MODIFIED;
                    } else if (currStatus == FieldPathIteratorHandler.ModificationStatus.MODIFIED) {
                        status = currStatus;
                    }
                }
                for (Field field : fieldsToRemove) {
                    removeFieldValue(field);
                }
            }
            return status;
        }
    }

    /**
     * Generated Document subclasses should override this and return true. This is used instead of using class.getAnnotation(Generated.class), because that is so slow.
     * @return true if in a concrete subtype of Document
     */
    protected boolean isGenerated() {
        return false;
    }

}
