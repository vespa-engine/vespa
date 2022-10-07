// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.DocumentUpdateWriter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A value update represents some action to perform to a value.
 *
 * @author Einar M R Rosenvinge
 * @see com.yahoo.document.update.FieldUpdate
 * @see com.yahoo.document.DocumentUpdate
 * @see AddValueUpdate
 * @see com.yahoo.document.update.ArithmeticValueUpdate
 * @see com.yahoo.document.update.AssignValueUpdate
 * @see com.yahoo.document.update.ClearValueUpdate
 * @see com.yahoo.document.update.MapValueUpdate
 * @see com.yahoo.document.update.RemoveValueUpdate
 */
public abstract class ValueUpdate<T extends FieldValue> {

    protected ValueUpdateClassID valueUpdateClassID;

    protected ValueUpdate(ValueUpdateClassID valueUpdateClassID) {
        this.valueUpdateClassID = valueUpdateClassID;
    }

    /** Returns the valueUpdateClassID of this value update. */
    public ValueUpdateClassID getValueUpdateClassID() {
        return valueUpdateClassID;
    }

    protected abstract void checkCompatibility(DataType fieldType);

    public abstract void serialize(DocumentUpdateWriter data, DataType superType);

    @Override
    public boolean equals(Object o) {
        return o instanceof ValueUpdate && valueUpdateClassID == ((ValueUpdate<?>) o).valueUpdateClassID;
    }

    @Override
    public int hashCode() {
        return valueUpdateClassID.id;
    }

    @Override
    public String toString() {
        return valueUpdateClassID.name;
    }

    public abstract FieldValue applyTo(FieldValue oldValue);

    /**
     * Creates a new value update specifying an addition of a value to an array or a key to a weighted set (with default weight 1).
     *
     * @param value the value to add to the array, or key to add to the weighted set
     * @return a ValueUpdate specifying the addition
     * @throws IllegalArgumentException      if the runtime type of newValue does not match the type required
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     */
    public static ValueUpdate createAdd(FieldValue value) {
        return new AddValueUpdate(value);
    }

    /**
     * Creates a new value update specifying an addition of a key (with a specified weight) to a weighted set. If this
     * method is used on an array data type, the weight will be omitted.
     *
     * @param key    the key to add
     * @param weight the weight to associate with the given key
     * @return a ValueUpdate specifying the addition
     * @throws IllegalArgumentException      if the runtime type of key does not match the type required
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     */
    public static ValueUpdate createAdd(FieldValue key, Integer weight) {
        return new AddValueUpdate(key, weight);
    }

    /**
     * Creates a new value update
     * specifying an addition of all values in a given list to an array. If this method is used on a weighted set data
     * type, the default weights will be 1. Note that this method is just a convenience method, it simply iterates
     * through the list and creates value updates by calling createAdd() for each element.
     *
     * @param values a List containing the values to add
     * @return a List of ValueUpdates specifying the addition
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see ValueUpdate#createAdd(FieldValue)
     */
    public static List<ValueUpdate> createAddAll(List<? extends FieldValue> values) {
        List<ValueUpdate> vupds = new ArrayList<>();
        for (FieldValue value : values) {
            vupds.add(ValueUpdate.createAdd(value));
        }
        return vupds;
    }

    /**
     * Creates a new value update
     * specifying an addition of all key/weight pairs in a weighted set to a weighted set. If this method
     * is used on an array data type, the weights will be omitted. Note that this method is just a convenience method,
     * it simply iterates through the set and creates value updates by calling createAdd() for each element.
     *
     * @param set a WeightedSet containing the key/weight pairs to add
     * @return a ValueUpdate specifying the addition
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required
     * @throws UnsupportedOperationException if the field type is not weighted set or array
     * @see ValueUpdate#createAdd(FieldValue, Integer)
     */
    public static List<ValueUpdate> createAddAll(WeightedSet<? extends FieldValue> set) {
        List<ValueUpdate> vupds = new ArrayList<>();
        Iterator<? extends FieldValue> it = set.fieldValueIterator();
        while (it.hasNext()) {
            FieldValue key = it.next();
            vupds.add(ValueUpdate.createAdd(key, set.get(key)));
        }
        return vupds;
    }

    /**
     * Creates a new value update that increments a value. Note that the data type must be a numeric
     * type.
     *
     * @param increment the number to increment by
     * @return a ValueUpdate specifying the increment
     * @throws UnsupportedOperationException if the data type is non-numeric
     */
    public static ValueUpdate createIncrement(Number increment) {
        return new ArithmeticValueUpdate(ArithmeticValueUpdate.Operator.ADD, increment);
    }

    /**
     * Creates a new value update that increments a weight in a weighted set. Note that this method is just a convenience
     * method, it simply creates an increment value update by calling createIncrement() and then creates a map value
     * update by calling createMap() with the key and the increment value update as parameters.
     *
     * @param key the key whose weight in the weighted set to increment
     * @param increment the number to increment by
     * @return a ValueUpdate specifying the increment
     * @see ValueUpdate#createIncrement(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static ValueUpdate createIncrement(FieldValue key, Number increment) {
        return createMap(key, createIncrement(increment));
    }

    /**
     * Creates a new value update that decrements a value. Note that the data type must be a numeric
     * type.
     *
     * @param decrement the number to decrement by
     * @return a ValueUpdate specifying the decrement
     * @throws UnsupportedOperationException if the data type is non-numeric
     */
    public static ValueUpdate createDecrement(Number decrement) {
        return new ArithmeticValueUpdate(ArithmeticValueUpdate.Operator.SUB, decrement);
    }

    /**
     * Creates a new value update that decrements a weight in a weighted set. Note that this method is just a convenience
     * method, it simply creates a decrement value update by calling createDecrement() and then creates a map value
     * update by calling createMap() with the key and the decrement value update as parameters.
     *
     * @param key the key whose weight in the weighted set to decrement
     * @param decrement the number to decrement by
     * @return a ValueUpdate specifying the decrement
     * @see ValueUpdate#createDecrement(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static ValueUpdate createDecrement(FieldValue key, Number decrement) {
        return createMap(key, createDecrement(decrement));
    }

    /**
     * Creates a new value update that multiplies a value. Note that the data type must be a numeric
     * type.
     *
     * @param factor the number to multiply by
     * @return a ValueUpdate specifying the multiplication
     * @throws UnsupportedOperationException if the data type is non-numeric
     */
    public static ValueUpdate createMultiply(Number factor) {
        return new ArithmeticValueUpdate(ArithmeticValueUpdate.Operator.MUL, factor);
    }

    /**
     * Creates a new value update that multiplies a weight in a weighted set. Note that this method is just a convenience
     * method, it simply creates a multiply value update by calling createMultiply() and then creates a map value
     * update by calling createMap() with the key and the multiply value update as parameters.
     *
     * @param key the key whose weight in the weighted set to multiply
     * @param factor the number to multiply by
     * @return a ValueUpdate specifying the multiplication
     * @see ValueUpdate#createMultiply(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static ValueUpdate createMultiply(FieldValue key, Number factor) {
        return createMap(key, createMultiply(factor));
    }


    /**
     * Creates a new value update that divides a value. Note that the data type must be a numeric
     * type.
     *
     * @param divisor the number to divide by
     * @return a ValueUpdate specifying the division
     * @throws UnsupportedOperationException if the data type is non-numeric
     */
    public static ValueUpdate createDivide(Number divisor) {
        return new ArithmeticValueUpdate(ArithmeticValueUpdate.Operator.DIV, divisor);
    }

    /**
     * Creates a new value update that divides a weight in a weighted set. Note that this method is just a convenience
     * method, it simply creates a divide value update by calling createDivide() and then creates a map value
     * update by calling createMap() with the key and the divide value update as parameters.
     *
     * @param key the key whose weight in the weighted set to divide
     * @param divisor the number to divide by
     * @return a ValueUpdate specifying the division
     * @see ValueUpdate#createDivide(Number)
     * @see ValueUpdate#createMap(FieldValue, ValueUpdate)
     */
    public static ValueUpdate createDivide(FieldValue key, Number divisor) {
        return createMap(key, createDivide(divisor));
    }

    /**
     * Creates a new value update that assigns a new value, completely overwriting
     * the previous value.
     *
     * @param newValue the value to assign
     * @return a ValueUpdate specifying the assignment
     * @throws IllegalArgumentException if the runtime type of newValue does not match the type required
     */
    public static ValueUpdate createAssign(FieldValue newValue) {
        return new AssignValueUpdate(newValue);
    }

    /**
     * Creates a new value update that clears the field fromthe document.
     *
     * @return a ValueUpdate specifying the removal
     */
    public static ValueUpdate createClear() {
        return new ClearValueUpdate();
    }

    /**
     * Creates a map value update, which is able to map an update to a value to a subvalue in an array or a
     * weighted set. If this update is to be applied to an array, the value parameter must be an integer specifying
     * the index in the array that the update parameter is to be applied to, and the update parameter must be
     * compatible with the sub-datatype of the array. If this update is to be applied on a weighted set, the value
     * parameter must be the key in the set that the update parameter is to be applied to, and the update parameter
     * must be compatible with the INT data type.
     *
     * @param value the index in case of array, or key in case of weighted set
     * @param update the update to apply to the target sub-value
     * @throws IllegalArgumentException in case data type is an array type and value is not an Integer; in case data type is a weighted set type and value is not equal to the nested type of the weighted set; or the encapsulated update throws such an exception
     * @throws UnsupportedOperationException if superType is a single-value type, or anything else than array or weighted set; or the encapsulated update throws such an exception
     * @return a ValueUpdate specifying the sub-update
     */
    public static ValueUpdate createMap(FieldValue value, ValueUpdate update) {
        return new MapValueUpdate(value, update);
    }

    /**
     * Creates a new value update specifying the removal of a value from an array or a key/weight from a weighted set.
     *
     * @param value the value to remove from the array, or key to remove from the weighted set
     * @return a ValueUpdate specifying the removal
     * @throws IllegalArgumentException      if the runtime type of newValue does not match the type required
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     */
    public static ValueUpdate createRemove(FieldValue value) {
        return new RemoveValueUpdate(value);
    }

    /**
     * Creates a new value update
     * specifying the removal of all values in a given list from an array or weighted set. Note that this method
     * is just a convenience method, it simply iterates
     * through the list and creates value updates by calling createRemove() for each element.
     *
     * @param values a List containing the values to remove
     * @return a List of ValueUpdates specifying the removal
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see ValueUpdate#createRemove(FieldValue)
     */
    public static List<ValueUpdate> createRemoveAll(List<? extends FieldValue> values) {
        List<ValueUpdate> vupds = new ArrayList<>();
        for (FieldValue value : values) {
            vupds.add(ValueUpdate.createRemove(value));
        }
        return vupds;
    }

    /**
     * Creates a new value update
     * specifying the removal of all values in a given list from an array or weighted set. Note that this method
     * is just a convenience method, it simply iterates
     * through the list and creates value updates by calling createRemove() for each element.
     *
     * @param values a List containing the values to remove
     * @return a List of ValueUpdates specifying the removal
     * @throws IllegalArgumentException      if the runtime type of values does not match the type required
     * @throws UnsupportedOperationException if the field type is not array or weighted set
     * @see ValueUpdate#createRemove(FieldValue)
     */
    public static List<ValueUpdate> createRemoveAll(WeightedSet<? extends FieldValue> values) {
        List<ValueUpdate> vupds = new ArrayList<>();
        for (FieldValue value : values.keySet()) {
            vupds.add(ValueUpdate.createRemove(value));
        }
        return vupds;
    }

    /** Returns the primary "value" of this update, or null if this kind of update has no value */
    public abstract T getValue();

    /** Sets the value of this. Ignored by update who have no value */
    public abstract void setValue(T value);

    public enum ValueUpdateClassID {
        //DO NOT change anything here unless you change src/vespa/document/util/identifiableid.h as well!!
        ADD(25, "add"),
        ARITHMETIC(26, "arithmetic"),
        ASSIGN(27, "assign"),
        CLEAR(28, "clear"),
        MAP(29, "map"),
        REMOVE(30, "remove"),
        TENSORMODIFY(100, "tensormodify"),
        TENSORADD(101, "tensoradd"),
        TENSORREMOVE(102, "tensorremove");

        public final int id;
        public final String name;

        ValueUpdateClassID(int id, String name) {
            this.id = 0x1000 + id;
            this.name = name;
        }

        public static ValueUpdateClassID getID(int id) {
            for (ValueUpdateClassID vucid : ValueUpdateClassID.values()) {
                if (vucid.id == id) {
                    return vucid;
                }
            }
            return null;
        }
    }
}
