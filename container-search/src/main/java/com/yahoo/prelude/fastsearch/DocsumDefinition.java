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

    private final String name;
    private final ImmutableList<DocsumField> fields;

    /** True if this contains dynamic fields */
    private final boolean dynamic;

    // Mapping between field names and their index in this.fields
    private final ImmutableMap<String, Integer> fieldNameToIndex;

    public DocsumDefinition(String name, List<DocsumField> fields) {
        this.name = name;
        this.dynamic = false;
        this.fields = ImmutableList.copyOf(fields);
        ImmutableMap.Builder<String, Integer> fieldNameToIndexBuilder = new ImmutableMap.Builder<>();
        int i = 0;
        for (DocsumField field : fields)
            fieldNameToIndexBuilder.put(field.name, i++);
        this.fieldNameToIndex = fieldNameToIndexBuilder.build();
    }

    // TODO: Remove LegacyEmulationConfig (the config, not just the usage) on Vespa 7
    DocsumDefinition(DocumentdbInfoConfig.Documentdb.Summaryclass config, LegacyEmulationConfig emulConfig) {
        this.name = config.name();

        List<DocsumField> fieldsBuilder = new ArrayList<>();
        Map<String, Integer> fieldNameToIndexBuilder = new HashMap<>();
        boolean dynamic = false;
        for (DocumentdbInfoConfig.Documentdb.Summaryclass.Fields field : config.fields()) {
            // no, don't switch the order of the two next lines :)
            fieldNameToIndexBuilder.put(field.name(), fieldsBuilder.size());
            fieldsBuilder.add(DocsumField.create(field.name(), field.type(), emulConfig));
            if (field.dynamic())
                dynamic = true;
        }
        this.dynamic = dynamic;
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
