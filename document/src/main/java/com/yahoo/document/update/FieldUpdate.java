// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.document.serialization.DocumentUpdateReader;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.io.GrowableByteBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>A field update holds a list of value updates that will be applied atomically to a field in a document.</p>
 * <p>To create a field update that contains only a single value update, use the static factory methods provided by this
 * class.</p> <p>Example:</p>
 * <pre>
 * FieldUpdate clearFieldUpdate = FieldUpdate.createClearField(field);
 * </pre>
 * <p>It is also possible to create a field update that holds more than one value update.</p>
 * <p>Example:</p>
 * <pre>
 * FieldUpdate fieldUpdate = FieldUpdate.create(field);
 * ValueUpdate incrementValue = ValueUpdate.createIncrement("foo", 130d);
 * ValueUpdate addValue = ValueUpdate.createAdd("bar", 100);
 * fieldUpdate.addValueUpdate(incrementValue);
 * fieldUpdate.addValueUpdate(addValue);</pre>
 * <p>Note that the addValueUpdate() method returns a reference to itself to support chaining, so the last two
 * lines could be written as one:</p>
 * <pre>
 * fieldUpdate.addValueUpdate(incrementValue).addValueUpdate(addValue);
 * </pre>
 * <p>Note also that the second example above is equivalent to:</p>
 * <pre>
 * FieldUpdate fieldUpdate = FieldUpdate.createIncrement(field, "foo", 130d);
 * ValueUpdate addValue = ValueUpdate.createAdd("bar", 100);
 * fieldUpdate.addValueUpdate(addValue);
 * </pre>
 * <p>Note that even though updates take fields as arguments, those fields are not necessarily a field of a document
 * type - any name/value pair which existing in an updatable structure can be addressed by creating the Fields as
 * needed. For example:
 * <pre>
 * FieldUpdate field = FieldUpdate.createIncrement(new Field("myattribute",DataType.INT),130);
 * </pre>
 *
 * @author Einar M R Rosenvinge
 * @see com.yahoo.document.update.ValueUpdate
 * @see com.yahoo.document.DocumentUpdate
 */
public class FieldUpdate {

    protected Field field;
    protected List<ValueUpdate> valueUpdates = new ArrayList<>();

    // Used only while deserializing.
    private DocumentType documentType = null;

    FieldUpdate(Field field) {
        this.field = field;
    }

    FieldUpdate(Field field, ValueUpdate valueUpd) {
        this(field);
        addValueUpdate(valueUpd);
    }

    FieldUpdate(Field field, List<ValueUpdate> valueUpdates) {
        this(field);
        addValueUpdates(valueUpdates);
    }

    public FieldUpdate(DocumentUpdateReader reader, DocumentType type) {
        documentType = type;
        reader.read(this);
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    /** Returns the field that this field update applies to */
    public Field getField() {
        return field;
    }

    /**
     * Sets the field this update applies to. Note that this does not need to be
     * a field of the document type in question - a field is just the name and type
     * of some value to be updated.
     */
    public void setField(Field field) {
        this.field = field;
    }

    /**
     * Applies this field update.
     *
     * @param doc the document to apply the update to
     * @return a reference to itself
     */
    public FieldUpdate applyTo(Document doc) {
        for (ValueUpdate vupd : valueUpdates) {
            DataType dataType = field.getDataType();
            FieldValue oldValue = doc.getFieldValue(field);
            boolean existed = (oldValue != null);

            if (!existed) {
                oldValue = dataType.createFieldValue();
            }

            FieldValue newValue = vupd.applyTo(oldValue);

            if (newValue == null) {
                if (existed) {
                    doc.removeFieldValue(field);
                }
            } else {
                doc.setFieldValue(field, newValue);
            }
        }
        return this;
    }

    /**
     * Adds a value update to the list of value updates.
     *
     * @param valueUpdate the ValueUpdate to add
     * @return a reference to itself
     * @throws IllegalArgumentException if the data type of the value update is not equal to the data type of this field
     */
    public FieldUpdate addValueUpdate(ValueUpdate valueUpdate) {
        valueUpdate.checkCompatibility(field.getDataType()); // will throw exception
        valueUpdates.add(valueUpdate);
        return this;
    }

    /**
     * Adds a value update to the list of value updates.
     *
     * @param index the index where this value update should be added
     * @param valueUpdate the ValueUpdate to add
     * @return a reference to itself
     * @throws IllegalArgumentException if the data type of the value update is not equal to the data type of this field
     */
    public FieldUpdate addValueUpdate(int index, ValueUpdate valueUpdate) {
        valueUpdate.checkCompatibility(field.getDataType()); // will throw exception
        valueUpdates.add(index, valueUpdate);
        return this;
    }

    /**
     * Adds a list of value updates to the list of value updates.
     *
     * @param valueUpdates a list containing the value updates to add
     * @return a reference to itself
     * @throws IllegalArgumentException if the data type of the value update is not equal to the data type of this field
     */
    public FieldUpdate addValueUpdates(List<ValueUpdate> valueUpdates) {
        for (ValueUpdate vupd : valueUpdates) {
            addValueUpdate(vupd);
        }
        return this;
    }

    /**
     * Removes the value update at the specified position in the list of value updates.
     *
     * @param index the index of the ValueUpdate to remove
     * @return the ValueUpdate previously at the specified position
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public ValueUpdate removeValueUpdate(int index) {
        return valueUpdates.remove(index);
    }

    /**
     * Replaces the value update at the specified position in the list of value updates
     * with the specified value update.
     *
     * @param index index of value update to replace
     * @param update value update to be stored at the specified position
     * @return the value update previously at the specified position
     * @throws IndexOutOfBoundsException if index out of range (index &lt; 0 || index &gt;= size())
     */
    public ValueUpdate setValueUpdate(int index, ValueUpdate update) {
        return valueUpdates.set(index, update);
    }

    /**
     * Get the number of value updates in this field update.
     *
     * @return the size of the List of FieldUpdates
     */
    public int size() {
        return valueUpdates.size();
    }

    /** Removes all value updates from the list of value updates. */
    public void clearValueUpdates() {
        valueUpdates.clear();
    }

    /**
     * Get the value update at the specified index in the list of value updates.
     *
     * @param index the index of the ValueUpdate to return
     * @return the ValueUpdate at the specified index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public ValueUpdate getValueUpdate(int index) {
        return valueUpdates.get(index);
    }

    /**
     * Get an unmodifiable list of all value updates that this field update specifies.
     *
     * @return a list of all ValueUpdates in this FieldUpdate
     */
    public List<ValueUpdate> getValueUpdates() {
        return Collections.unmodifiableList(valueUpdates);
    }

    /**
     * Get value updates with the specified valueUpdateClassID. The caller gets ownership of the returned list, and
     * subsequent modifications to the list does not change the state of this object.
     *
     * @param classID the classID of ValueUpdates to return
     * @return a List of ValueUpdates of the specified classID (possibly empty, but not null)
     */
    public List<ValueUpdate> getValueUpdates(ValueUpdate.ValueUpdateClassID classID) {
        List<ValueUpdate> updateList = new ArrayList<>();
        for (ValueUpdate vupd : valueUpdates) {
            if (vupd.getValueUpdateClassID() == classID) {
                updateList.add(vupd);
            }
        }
        return updateList;
    }

    /**
     * Returns whether this field update contains (at least) one update of the given type
     *
     * @param classID the classID of ValueUpdates to check for
     * @return true if there is at least one value update of the given type in this
     */
    public boolean hasValueUpdate(ValueUpdate.ValueUpdateClassID classID) {
        for (ValueUpdate vupd : valueUpdates) {
            if (vupd.getValueUpdateClassID() == classID) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether or not this field update contains any value updates.
     *
     * @return True if this update is empty.
     */
    public boolean isEmpty() {
        return valueUpdates.isEmpty();
    }

    /**
     * Adds all the {@link ValueUpdate}s of the given FieldUpdate to this. If the given FieldUpdate refers to a
     * different {@link Field} than this, this method throws an exception.
     *
     * @param update The update whose content to add to this.
     * @throws IllegalArgumentException If the {@link Field} of the given FieldUpdate does not match this.
     */
    public void addAll(FieldUpdate update) {
        if (update == null) {
            return;
        }
        if (!field.equals(update.field)) {
            throw new IllegalArgumentException("Expected " + field + ", got " + update.field + ".");
        }
        addValueUpdates(update.valueUpdates);
    }

    public final void serialize(GrowableByteBuffer buf) {
        serialize(DocumentSerializerFactory.create6(buf));
    }

    public void serialize(DocumentUpdateWriter data) {
        data.write(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FieldUpdate && field.equals(((FieldUpdate)o).field) &&
               valueUpdates.equals(((FieldUpdate)o).valueUpdates);
    }

    @Override
    public int hashCode() {
        return field.getId() + valueUpdates.hashCode();
    }

    @Override
    public String toString() {
        return "'" + field.getName() + "' " + valueUpdates;
    }

    /**
     * Creates a new, empty field update with no encapsulated value updates. Use this method to add an arbitrary
     * set of value updates using the FieldUpdate.addValueUpdate() method.
     *
     * @param field the Field to alter
     * @return a new, empty FieldUpdate
     * @see com.yahoo.document.update.ValueUpdate
     * @see FieldUpdate#addValueUpdate(ValueUpdate)
     */
    public static FieldUpdate create(Field field) {
        return new FieldUpdate(field);
    }

    /**
     * Creates a new field update that clears the field. This operation removes the value/field completely.
     *
     * @param field the Field to clear
     * @return a FieldUpdate specifying the clearing
     * @see com.yahoo.document.update.FieldUpdate#createClear(Field)
     */
    public static FieldUpdate createClearField(Field field) {
        return new FieldUpdate(field, ValueUpdate.createClear());
    }

    /**
     * Creates a new field update, with one encapsulated value update
     * specifying an addition of a value to an array or a key to a weighted set (with default weight 1).
     *
     * @param field the Field to add a value to
     * @param value the value to add to the array, or key to add to the weighted set
     * @return a FieldUpdate specifying the addition
     * @throws IllegalArgumentException      if the runtime type of newValue does not match the type required by field
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see com.yahoo.document.update.ValueUpdate#createAdd(FieldValue)
     */
    public static FieldUpdate createAdd(Field field, FieldValue value) {
        return new FieldUpdate(field, ValueUpdate.createAdd(value));
    }

    /**
     * Creates a new field update, with one encapsulated value update
     * specifying an addition of a key (with a specified weight) to a weighted set. If this
     * method is used on an array data type, the weight will be omitted.
     *
     * @param field  the Field to a add a key to
     * @param key    the key to add
     * @param weight the weight to associate with the given key
     * @return a FieldUpdate specifying the addition
     * @throws IllegalArgumentException      if the runtime type of key does not match the type required by field
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see com.yahoo.document.update.ValueUpdate#createAdd(FieldValue,Integer)
     */
    public static FieldUpdate createAdd(Field field, FieldValue key, Integer weight) {
        return new FieldUpdate(field, ValueUpdate.createAdd(key, weight));
    }

    /**
     * Creates a new field update, with encapsulated value updates,
     * specifying an addition of all values in a given list to an array. If this method is used on a weighted set data
     * type, the default weights will be 1.
     *
     * @param field  the Field to add an array of values to
     * @param values a List containing the values to add
     * @return a FieldUpdate specifying the addition
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required by field
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see com.yahoo.document.update.ValueUpdate#createAddAll(java.util.List)
     * @see ValueUpdate#createAdd(FieldValue)
     */
    public static FieldUpdate createAddAll(Field field, List<? extends FieldValue> values) {
        return new FieldUpdate(field, ValueUpdate.createAddAll(values));
    }

    /**
     * Creates a new field update, with encapsulated value updates,
     * specifying an addition of all key/weight pairs in a weighted set to a weighted set. If this method
     * is used on an array data type, the weights will be omitted.
     *
     * @param field the Field to add the key/weight pairs in a weighted set to
     * @param set a WeightedSet containing the key/weight pairs to add
     * @return a FieldUpdate specifying the addition
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required by field
     * @throws UnsupportedOperationException if the field type is not weighted set or array
     * @see ValueUpdate#createAdd(FieldValue, Integer)
     * @see com.yahoo.document.update.ValueUpdate#createAddAll(com.yahoo.document.datatypes.WeightedSet)
     */
    public static FieldUpdate createAddAll(Field field, WeightedSet<? extends FieldValue> set) {
        return new FieldUpdate(field, ValueUpdate.createAddAll(set));
    }

    /**
     * Creates a new field update, with one encapsulated value update that increments a value.
     * Note that the data type must be a numeric type.
     *
     * @param field     the field to increment the value of
     * @param increment the number to increment by
     * @return a FieldUpdate specifying the increment
     * @throws UnsupportedOperationException if the data type is non-numeric
     * @see ValueUpdate#createIncrement(Number)
     */
    public static FieldUpdate createIncrement(Field field, Number increment) {
        return new FieldUpdate(field, ValueUpdate.createIncrement(increment));
    }

    /**
     * Creates a new field update, with one encapsulated value update that increments a weight in a weighted set.
     *
     * @param field     the field to increment one of the weights of
     * @param key the key whose weight in the weighted set to increment
     * @param increment the number to increment by
     * @return a FieldUpdate specifying the increment
     * @throws IllegalArgumentException if key is not equal to the nested type of the weighted set
     * @see ValueUpdate#createIncrement(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static FieldUpdate createIncrement(Field field, FieldValue key, Number increment) {
        return new FieldUpdate(field, ValueUpdate.createIncrement(key, increment));
    }

    /**
     * Creates a new field update, with one encapsulated value update that decrements a value.
     * Note that the data type must be a numeric type.
     *
     * @param field     the field to decrement the value of
     * @param decrement the number to decrement by
     * @return a FieldUpdate specifying the decrement
     * @throws UnsupportedOperationException if the data type is non-numeric
     * @see ValueUpdate#createDecrement(Number)
     */
    public static FieldUpdate createDecrement(Field field, Number decrement) {
        return new FieldUpdate(field, ValueUpdate.createDecrement(decrement));
    }

    /**
     * Creates a new field update, with one encapsulated value update that decrements a weight in a weighted set.
     *
     * @param field     the field to decrement one of the weights of
     * @param key the key whose weight in the weighted set to decrement
     * @param decrement the number to decrement by
     * @return a FieldUpdate specifying the decrement
     * @throws IllegalArgumentException if key is not equal to the nested type of the weighted set
     * @see ValueUpdate#createDecrement(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static FieldUpdate createDecrement(Field field, FieldValue key, Number decrement) {
        return new FieldUpdate(field, ValueUpdate.createDecrement(key, decrement));
    }

    /**
     * Creates a new field update, with one encapsulated value update that multiplies a value.
     * Note that the data type must be a numeric type.
     *
     * @param field     the field to multiply the value of
     * @param factor the number to multiply by
     * @return a FieldUpdate specifying the multiplication
     * @throws UnsupportedOperationException if the data type is non-numeric
     * @see ValueUpdate#createMultiply(Number)
     */
    public static FieldUpdate createMultiply(Field field, Number factor) {
        return new FieldUpdate(field, ValueUpdate.createMultiply(factor));
    }

    /**
     * Creates a new field update, with one encapsulated value update that multiplies a weight in a weighted set.
     *
     * @param field     the field to multiply one of the weights of
     * @param key the key whose weight in the weighted set to multiply
     * @param factor the number to multiply by
     * @return a FieldUpdate specifying the multiplication
     * @throws IllegalArgumentException if key is not equal to the nested type of the weighted set
     * @see ValueUpdate#createMultiply(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static FieldUpdate createMultiply(Field field, FieldValue key, Number factor) {
        return new FieldUpdate(field, ValueUpdate.createMultiply(key, factor));
    }

    /**
     * Creates a new field update, with one encapsulated value update that divides a value.
     * Note that the data type must be a numeric type.
     *
     * @param field     the field to divide the value of
     * @param divisor the number to divide by
     * @return a FieldUpdate specifying the division
     * @throws UnsupportedOperationException if the data type is non-numeric
     * @see ValueUpdate#createDivide(Number)
     */
    public static FieldUpdate createDivide(Field field, Number divisor) {
        return new FieldUpdate(field, ValueUpdate.createDivide(divisor));
    }

    /**
     * Creates a new field update, with one encapsulated value update that divides a weight in a weighted set.
     *
     * @param field     the field to divide one of the weights of
     * @param key the key whose weight in the weighted set to divide
     * @param divisor the number to divide by
     * @return a FieldUpdate specifying the division
     * @throws IllegalArgumentException if key is not equal to the nested type of the weighted set
     * @see ValueUpdate#createDivide(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static FieldUpdate createDivide(Field field, FieldValue key, Number divisor) {
        return new FieldUpdate(field, ValueUpdate.createDivide(key, divisor));
    }

    /**
     * Creates a new field update, with one encapsulated value update,
     * that assigns a new value, completely overwriting the previous value. Note that it is possible to pass
     * newValue=null to this method to remove the value completely.
     *
     * @param field    the Field to assign a new value to
     * @param newValue the value to assign
     * @return a FieldUpdate specifying the assignment
     * @throws IllegalArgumentException if the runtime type of newValue does not match the type required by field
     * @see com.yahoo.document.update.ValueUpdate#createAssign(FieldValue)
     */
    public static FieldUpdate createAssign(Field field, FieldValue newValue) {
        return new FieldUpdate(field, ValueUpdate.createAssign(newValue));
    }

    /**
     * Creates a new field update, with one encapsulated value update,
     * that clears the value; see documentation for ClearValueUpdate to see behavior
     * for the individual data types. Note that clearing the value is not the same
     * clearing the field; this method leaves an empty value, whereas clearing the
     * field will completely remove the value.
     *
     * @param field the field to clear the value of
     * @return a FieldUpdate specifying the clearing
     * @see com.yahoo.document.update.ClearValueUpdate
     * @see ValueUpdate#createClear()
     * @see FieldUpdate#createClearField(com.yahoo.document.Field)
     */
    public static FieldUpdate createClear(Field field) {
        return new FieldUpdate(field, ValueUpdate.createClear());
    }

    /**
     * Creates a new field update, with one encapsulated value update, which
     * is able to map an update to a value to a subvalue in an array or a
     * weighted set. If this update is to be applied to an array, the value parameter must be an integer specifying
     * the index in the array that the update parameter is to be applied to, and the update parameter must be
     * compatible with the sub-datatype of the array. If this update is to be applied on a weighted set, the value
     * parameter must be the key in the set that the update parameter is to be applied to, and the update parameter
     * must be compatible with the INT data type.
     *
     * @param field the field to modify the subvalue of
     * @param value the index in case of array, or key in case of weighted set
     * @param update the update to apply to the target sub-value
     * @throws IllegalArgumentException in case data type is an array type and value is not an Integer; in case data type is a weighted set type and value is not equal to the nested type of the weighted set; or the encapsulated update throws such an exception
     * @throws UnsupportedOperationException if superType is a single-value type, or anything else than array or weighted set; or the encapsulated update throws such an exception
     * @return a FieldUpdate specifying the sub-update
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static FieldUpdate createMap(Field field, FieldValue value, ValueUpdate update) {
        return new FieldUpdate(field, ValueUpdate.createMap(value, update));
    }

    /**
     * Creates a new field update, with one encapsulated value update,
     * specifying the removal of a value from an array or a key/weight from a weighted set.
     *
     * @param field the field to remove a value from
     * @param value the value to remove from the array, or key to remove from the weighted set
     * @return a FieldUpdate specifying the removal
     * @throws IllegalArgumentException      if the runtime type of newValue does not match the type required
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see ValueUpdate#createRemove(FieldValue)
     */
    public static FieldUpdate createRemove(Field field, FieldValue value) {
        return new FieldUpdate(field, ValueUpdate.createRemove(value));
    }

    /**
     * Creates a new field update, with encapsulated value updates,
     * specifying the removal of all values in a given list from an array or weighted set. Note that this method
     * is just a convenience method, it simply iterates
     * through the list and creates value updates by calling createRemove() for each element.
     *
     * @param field the field to remove values from
     * @param values a List containing the values to remove
     * @return a FieldUpdate specifying the removal
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required by field
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see ValueUpdate#createRemoveAll(java.util.List)
     */
    public static FieldUpdate createRemoveAll(Field field, List<? extends FieldValue> values) {
        return new FieldUpdate(field, ValueUpdate.createRemoveAll(values));
    }

    /**
     * Creates a new field update, with encapsulated value updates,
     * specifying the removal of all values in a given list from an array or weighted set. Note that this method
     * is just a convenience method, it simply iterates
     * through the list and creates value updates by calling createRemove() for each element.
     *
     * @param field the field to remove values from
     * @param values a List containing the values to remove
     * @return a FieldUpdate specifying the removal
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required by field
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see ValueUpdate#createRemoveAll(java.util.List)
     */
    public static FieldUpdate createRemoveAll(Field field, WeightedSet<? extends FieldValue> values) {
        return new FieldUpdate(field, ValueUpdate.createRemoveAll(values));
    }
}
