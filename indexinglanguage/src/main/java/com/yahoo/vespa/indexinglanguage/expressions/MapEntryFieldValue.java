// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;

/**
 * A field value which represents a map entry. Used to access map entries in for_each.
 *
 * @author bratseth
 */
class MapEntryFieldValue extends FieldValue {

    private final MapDataType type;

    private FieldValue key = null;
    private FieldValue value = null;

    MapEntryFieldValue(FieldValue key, FieldValue value) {
        this.type = new MapDataType(key.getDataType(), value.getDataType());
        this.key = key;
        this.value = value;
    }

    public FieldValue getKey() { return key; }

    public void setKey(FieldValue key) {
        if ( ! type.getKeyType().isAssignableFrom(key.getDataType()))
            throw new IllegalArgumentException("Got " + key.getDataType() + " but require " + type.getKeyType());
        this.key = key;
    }

    public FieldValue getValue() { return value; }

    public void setValue(FieldValue value) {
        if ( ! type.getValueType().isAssignableFrom(value.getDataType()))
            throw new IllegalArgumentException("Got " + value.getDataType() + " but require " + type.getValueType());
        this.value = value;
    }

    @Override
    public DataType getDataType() { return type; }

    @Override
    @SuppressWarnings("deprecation")
    public void printXml(XmlStream xml) { }

    @Override
    public void clear() {
        key = null;
        value = null;
    }

    @Override
    public void assign(Object o) {
        throw new IllegalArgumentException("Cannot be assigned a single value");
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        throw new IllegalArgumentException("Cannot be serialized");
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        throw new IllegalArgumentException("Cannot be deserialized");
    }

}
