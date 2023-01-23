// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.*;

import java.util.*;

/**
 * @author Simon Thoresen Hult
 */
public abstract class FieldValueConverter {

    @SuppressWarnings({ "unchecked" })
    public final FieldValue convert(FieldValue value) {
        if (value == null) {
            return null;
        }
        if (shouldConvert(value)) {
            return doConvert(value);
        }
        if (value instanceof Array) {
            return convertArray((Array)value);
        }
        if (value instanceof MapFieldValue) {
            return convertMap((MapFieldValue)value);
        }
        if (value instanceof WeightedSet) {
            return convertWset((WeightedSet)value);
        }
        if (value instanceof StructuredFieldValue) {
            return convertStructured((StructuredFieldValue)value);
        }
        return value;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private FieldValue convertArray(Array val) {
        List<FieldValue> next = new LinkedList<FieldValue>();
        DataType nextType = null;
        for (Iterator<FieldValue> it = val.fieldValueIterator(); it.hasNext();) {
            FieldValue prevVal = it.next();
            FieldValue nextVal = convert(prevVal);
            if (nextVal == null) {
                continue;
            }
            if (nextType == null) {
                nextType = nextVal.getDataType();
            } else if (!nextType.isValueCompatible(nextVal)) {
                throw new IllegalArgumentException("Expected " + nextType.getName() + ", got " +
                                                   nextVal.getDataType().getName() + ".");
            }
            next.add(nextVal);
        }
        if (nextType == null) {
            return null;
        }
        Array ret = DataType.getArray(nextType).createFieldValue();
        ret.addAll(next);
        return ret;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private FieldValue convertMap(MapFieldValue<FieldValue, FieldValue> val) {
        Map<FieldValue, FieldValue> next = new LinkedHashMap<FieldValue, FieldValue>();
        DataType nextKeyType = null, nextValType = null;
        for (Map.Entry<FieldValue, FieldValue> entry : val.entrySet()) {
            FieldValue prevKey = entry.getKey();
            FieldValue nextKey = convert(prevKey);
            if (nextKey == null) {
                continue;
            }
            if (nextKeyType == null) {
                nextKeyType = nextKey.getDataType();
            } else if (!nextKeyType.isValueCompatible(nextKey)) {
                throw new IllegalArgumentException("Expected " + nextKeyType.getName() + ", got " +
                                                   nextKey.getDataType().getName() + ".");
            }
            FieldValue prevVal = entry.getValue();
            FieldValue nextVal = convert(prevVal);
            if (nextVal == null) {
                continue;
            }
            if (nextValType == null) {
                nextValType = nextVal.getDataType();
            } else if (!nextValType.isValueCompatible(nextVal)) {
                throw new IllegalArgumentException("Expected " + nextValType.getName() + ", got " +
                                                   nextVal.getDataType().getName() + ".");
            }
            next.put(nextKey, nextVal);
        }
        if (nextKeyType == null || nextValType == null) {
            return null;
        }
        MapFieldValue ret = DataType.getMap(nextKeyType, nextValType).createFieldValue();
        ret.putAll(next);
        return ret;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private FieldValue convertWset(WeightedSet val) {
        Map<FieldValue, Integer> next = new LinkedHashMap<FieldValue, Integer>();
        DataType nextType = null;
        for (Iterator<FieldValue> it = val.fieldValueIterator(); it.hasNext();) {
            FieldValue prevKey = it.next();
            Integer prevVal = val.get(prevKey);

            FieldValue nextKey = convert(prevKey);
            if (nextKey == null) {
                continue;
            }
            if (nextType == null) {
                nextType = nextKey.getDataType();
            } else if (!nextType.isValueCompatible(nextKey)) {
                throw new IllegalArgumentException("Expected " + nextType.getName() + ", got " +
                                                   nextKey.getDataType().getName() + ".");
            }
            next.put(nextKey, prevVal);
        }
        if (nextType == null) {
            return null;
        }
        WeightedSet ret = DataType.getWeightedSet(nextType, val.getDataType().createIfNonExistent(),
                                                  val.getDataType().removeIfZero()).createFieldValue();

        ret.putAll(next);
        return ret;
    }

    private FieldValue convertStructured(StructuredFieldValue val) {
        StructuredFieldValue ret = val.getDataType().createFieldValue();
        for (Iterator<Map.Entry<Field, FieldValue>> it = val.iterator(); it.hasNext();) {
            Map.Entry<Field, FieldValue> entry = it.next();
            FieldValue prev = entry.getValue();
            FieldValue next = convert(prev);
            if (next == null) {
                continue;
            }
            ret.setFieldValue(entry.getKey(), next);
        }
        return ret;
    }

    /**
     * Returns whether or not the given {@link FieldValue} should be converted. If this method returns <em>false</em>,
     * the converter will proceed to traverse the value itself to see if its internal can be converted.
     *
     * @param value the value to check
     * @return true to convert, false to traverse
     */
    protected abstract boolean shouldConvert(FieldValue value);

    /**
     * Converts the given value. It is IMPERATIVE that the implementation of this method DOES NOT mutate the given
     * {@link FieldValue} in place, as that can cause SERIOUS inconsistencies in the parent structures.
     *
     * @param value the value to convert
     * @return the value to replace the old
     */
    protected abstract FieldValue doConvert(FieldValue value);

}
