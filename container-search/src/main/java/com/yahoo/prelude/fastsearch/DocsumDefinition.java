// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.container.search.LegacyEmulationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A docsum definition which knows how to decode a certain class of document
 * summaries. The docsum definition has a name and a list of field definitions
 *
 * @author bratseth
 * @author Bjørn Borud
 */
public class DocsumDefinition {

    private String name;
    private final List<DocsumField> fields;

    /** True if this contains dynamic fields */
    private boolean dynamic = false;

    // Mapping between field names and their index in this.fields
    private final Map<String,Integer> fieldNameToIndex;

    DocsumDefinition(DocumentdbInfoConfig.Documentdb.Summaryclass config, LegacyEmulationConfig emulConfig) {
        this.name = config.name();
        List<DocsumField> fieldsBuilder = new ArrayList<>();
        Map<String,Integer> fieldNameToIndexBuilder = new HashMap<>();

        for (DocumentdbInfoConfig.Documentdb.Summaryclass.Fields field : config.fields()) {
            // no, don't switch the order of the two next lines :)
            fieldNameToIndexBuilder.put(field.name(), fieldsBuilder.size());
            fieldsBuilder.add(DocsumField.create(field.name(), field.type(), emulConfig));
            if (field.dynamic())
                dynamic = true;
        }
        fields = ImmutableList.copyOf(fieldsBuilder);
        fieldNameToIndex = ImmutableMap.copyOf(fieldNameToIndexBuilder);
    }

    /** Returns the field at this index, or null if none */
    public DocsumField getField(int fieldIndex) {
        if (fieldIndex >= fields.size()) return null;
        return fields.get(fieldIndex);
    }

    /** Returns the index of a field name */
    public Integer getFieldIndex(String fieldName) {
        return fieldNameToIndex.get(fieldName);
    }

    @Override
    public String toString() {
        return "docsum definition '" + getName() + "'";
    }

    public String getName() {
        return name;
    }

    public int getFieldCount() {
        return fields.size();
    }

    public List<DocsumField> getFields() {
        return fields;
    }

    /** Returns whether this summary contains one or more dynamic fields */
    public boolean isDynamic() {
        return dynamic;
    }

}
