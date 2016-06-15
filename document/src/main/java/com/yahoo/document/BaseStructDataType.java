// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;

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
    protected Map<Integer, Field> fieldIdsV6 = new LinkedHashMap<>();
    protected Map<String, Field> fields = new LinkedHashMap<>();

    protected Compressor compressor = new Compressor(CompressionType.NONE);

    BaseStructDataType(String name) {
        super(name);
    }

    BaseStructDataType(int id, String name) {
        super(id, name);
    }

    protected void assign(BaseStructDataType type) {
        BaseStructDataType stype = type.clone();

        fieldIds = stype.fieldIds;
        fieldIdsV6 = stype.fieldIdsV6;
        fields = stype.fields;
    }

    @Override
    public BaseStructDataType clone() {
        BaseStructDataType type = (BaseStructDataType) super.clone();
        type.fieldIds = new LinkedHashMap<>();
        type.fieldIdsV6 = new LinkedHashMap<>();

        type.fields = new LinkedHashMap<>();
        for (Field field : fieldIds.values()) {
            type.fields.put(field.getName(), field);
            type.fieldIds.put(field.getId(Document.SERIALIZED_VERSION), field);
            type.fieldIdsV6.put(field.getId(6), field);
        }
        return type;
    }

    public void addField(Field field) {
        if (fields.containsKey(field.getName())) {
            throw new IllegalArgumentException("Struct " + getName() + " already contains field with name " + field.getName());
        }
        if (fieldIds.containsKey(field.getId(Document.SERIALIZED_VERSION))) {
            throw new IllegalArgumentException("Struct " + getName() + " already contains field with id " + field.getId(Document.SERIALIZED_VERSION));
        }
        if (fieldIdsV6.containsKey(field.getId(6))) {
            throw new IllegalArgumentException("Struct " + getName() + " already contains a field with deprecated document serialization id " + field.getId(6));
        }

        fields.put(field.getName(), field);
        fieldIds.put(field.getId(Document.SERIALIZED_VERSION), field);
        fieldIdsV6.put(field.getId(6), field);
    }

    public Field removeField(String fieldName) {
        Field old = fields.remove(fieldName);
        if (old != null) {
            fieldIds.remove(old.getId(Document.SERIALIZED_VERSION));
            fieldIdsV6.remove(old.getId(6));
        }
        return old;
    }

    public void clearFields() {
        fieldIds.clear();
        fieldIdsV6.clear();
        fields.clear();
    }

    public Field getField(Integer fieldId, int version) {
        if (version > 6) {
            return fieldIds.get(fieldId);
        } else {
            return fieldIdsV6.get(fieldId);
        }
    }

    @Override
    public Field getField(String fieldName) {
        return fields.get(fieldName);
    }

    @Override
    public Field getField(int id) {
        return fieldIds.get(id);
    }

    public boolean hasField(Field field, int version) {
        Field f = getField(field.getId(version), version);
        return f != null && f.equals(field);
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    public boolean hasField(Field f) {
        if (hasField(f, 6) || hasField(f, Document.SERIALIZED_VERSION)) {
            return true;
        }
        return false;
    }

    @Override
    public Collection<Field> getFields() {
        return fields.values();
    }

    public int getFieldCount() {
        return fields.size();
    }

    /** Returns the compressor to use to compress data of this type */
    public Compressor getCompressor() { return compressor; }

    /** Returns a view of the configuration of the compressor used to compress this type */
    public CompressionConfig getCompressionConfig() {
        // CompressionConfig accepts a percentage (but exposes a factor) ...
        float compressionThresholdPercentage = (float)compressor.compressionThresholdFactor() * 100;

        return new CompressionConfig(compressor.type(),
                                     compressor.level(),
                                     compressionThresholdPercentage,
                                     compressor.compressMinSizeBytes());
    }

    /** Set the config to the compressor used to compress data of this type */
    public void setCompressionConfig(CompressionConfig config) {
        CompressionType type = config.type;
        compressor = new Compressor(type,
                                    config.compressionLevel,
                                    config.thresholdFactor(),
                                    (int)config.minsize);
    }

}
