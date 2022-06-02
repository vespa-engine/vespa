// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a map type.
 *
 * @author vegardh
 */
public class MapDataType extends DataType {

    private DataType keyType;
    private DataType valueType;

    public MapDataType(DataType keyType, DataType valueType, int id) {
        super("Map<"+keyType.getName()+","+valueType.getName()+">", id);
        this.keyType=keyType;
        this.valueType = valueType;
    }

    public MapDataType(DataType keyType, DataType valueType) {
        this(keyType, valueType, 0);
        setId(getName().toLowerCase().hashCode());
    }

    @Override
    public MapDataType clone() {
        MapDataType type = (MapDataType)super.clone();
        type.keyType = keyType.clone();
        type.valueType = valueType.clone();
        return type;
    }

    @Override
    protected FieldValue createByReflection(Object arg) { return null; }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        return value.getDataType().equals(this);
    }

    public DataType getKeyType() {
        return keyType;
    }

    public DataType getValueType() {
        return valueType;
    }

    @Override
    public MapFieldValue createFieldValue() {
        return new MapFieldValue(this);
    }

    @Override
    public Class getValueClass() {
        return MapFieldValue.class;
    }

    @Override
    protected void register(DocumentTypeManager manager,
            List<DataType> seenTypes) {
        seenTypes.add(this);
        if (!seenTypes.contains(getKeyType())) {
            getKeyType().register(manager, seenTypes);
        }
        if (!seenTypes.contains(getValueType())) {
            getValueType().register(manager, seenTypes);
        }
        super.register(manager, seenTypes);
    }

    public static FieldPath buildFieldPath(String remainFieldName, DataType keyType, DataType valueType) {
        if (remainFieldName.length() > 0 && remainFieldName.charAt(0) == '{') {
            FieldPathEntry.KeyParseResult result = FieldPathEntry.parseKey(remainFieldName);
            String keyValue = result.parsed;

            FieldPath path = valueType.buildFieldPath(skipDotInString(remainFieldName, result.consumedChars - 1));
            List<FieldPathEntry> tmpPath = new ArrayList<FieldPathEntry>(path.getList());

            if (remainFieldName.charAt(1) == '$') {
                tmpPath.add(0, FieldPathEntry.newVariableLookupEntry(keyValue.substring(1), valueType));
            } else {
                FieldValue fv = keyType.createFieldValue();
                fv.assign(keyValue);
                tmpPath.add(0, FieldPathEntry.newMapLookupEntry(fv, valueType));
            }

            return new FieldPath(tmpPath);

        } else if (remainFieldName.startsWith("key")) {
            FieldPath path = keyType.buildFieldPath(skipDotInString(remainFieldName, 2));
            List<FieldPathEntry> tmpPath = new ArrayList<FieldPathEntry>(path.getList());
            tmpPath.add(0, FieldPathEntry.newAllKeysLookupEntry(keyType));
            return new FieldPath(tmpPath);
        } else if (remainFieldName.startsWith("value")) {
            FieldPath path = valueType.buildFieldPath(skipDotInString(remainFieldName, 4));
            List<FieldPathEntry> tmpPath = new ArrayList<FieldPathEntry>(path.getList());
            tmpPath.add(0, FieldPathEntry.newAllValuesLookupEntry(valueType));
            return new FieldPath(tmpPath);
        }

        return keyType.buildFieldPath(remainFieldName);
    }

    @Override
    public FieldPath buildFieldPath(String remainFieldName) {
        return buildFieldPath(remainFieldName, getKeyType(), getValueType());
    }

    @Override
    public boolean isMultivalue() { return true; }

}
