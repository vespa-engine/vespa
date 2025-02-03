// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.*;

import java.util.*;

/**
 * @author Simon Thoresen Hult
 */
public abstract class FieldValueConverter {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final FieldValue convert(FieldValue value) {
        if (value == null) return null;
        if (shouldConvert(value)) return doConvert(value);
        if (value instanceof Array arrayValue) return convertArray(arrayValue);
        if (value instanceof MapFieldValue mapValue) return convertMap(mapValue);
        if (value instanceof WeightedSet weightedSetValue) return convertWset(weightedSetValue);
        if (value instanceof StructuredFieldValue structuredFieldValue) return convertStructured(structuredFieldValue);
        return value;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private FieldValue convertArray(Array val) {
        List<FieldValue> next = new LinkedList<>();
        DataType type = null;
        for (Iterator<FieldValue> i = val.fieldValueIterator(); i.hasNext();) {
            FieldValue value = convert(i.next());
            if (value == null) continue;
            if (type == null) {
                type = value.getDataType();
            } else if (!type.isValueCompatible(value)) {
                throw new IllegalArgumentException("Expected " + type.getName() + ", got " +
                                                   value.getDataType().getName());
            }
            next.add(value);
        }
        if (type == null) return null;

        Array convertedValue = DataType.getArray(type).createFieldValue();
        convertedValue.addAll(next);
        return convertedValue;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected FieldValue convertMap(MapFieldValue<FieldValue, FieldValue> val) {
        Map<FieldValue, FieldValue> convertedMap = new LinkedHashMap<>();
        DataType keyType = null;
        DataType valueType = null;
        for (Map.Entry<FieldValue, FieldValue> entry : val.entrySet()) {
            FieldValue key = convert(entry.getKey());
            if (key == null) continue;

            if (keyType == null) {
                keyType = key.getDataType();
            } else if (!keyType.isValueCompatible(key)) {
                throw new IllegalArgumentException("Expected " + keyType.getName() + ", got " +
                                                   key.getDataType().getName());
            }
            FieldValue value = convert(entry.getValue());
            if (value == null) continue;

            if (valueType == null) {
                valueType = value.getDataType();
            } else if (!valueType.isValueCompatible(value)) {
                throw new IllegalArgumentException("Expected " + valueType.getName() + ", got " +
                                                   value.getDataType().getName());
            }
            convertedMap.put(key, value);
        }
        if (keyType == null || valueType == null) return null;

        MapFieldValue convertedValue = DataType.getMap(keyType, valueType).createFieldValue();
        convertedValue.putAll(convertedMap);
        return convertedValue;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private FieldValue convertWset(WeightedSet val) {
        Map<FieldValue, Integer> next = new LinkedHashMap<>();
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
                                                   nextKey.getDataType().getName());
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
     * Returns whether the given {@link FieldValue} should be converted. If this method returns <em>false</em>,
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
