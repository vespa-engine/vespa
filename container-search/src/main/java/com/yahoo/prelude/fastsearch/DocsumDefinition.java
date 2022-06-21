// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;
import com.yahoo.search.schema.DocumentSummary;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A docsum definition which knows how to decode a certain class of document
 * summaries. The docsum definition has a name and a list of field definitions
 *
 * @author bratseth
 * @author Bjørn Borud
 */
public class DocsumDefinition {

    private final String name;
    private final Map<String, DocsumField> fields;

    /** True if this contains dynamic fields */
    private final boolean dynamic;

    public DocsumDefinition(DocumentSummary documentSummary) {
        this.name = documentSummary.name();
        this.dynamic = documentSummary.isDynamic();
        this.fields = documentSummary.fields().values()
                                     .stream()
                                     .map(field -> DocsumField.create(field.name(), field.type().asString()))
                                     .collect(Collectors.toUnmodifiableMap(field -> field.getName(),
                                                                           field -> field));
    }

    public String name() { return name; }
    public Map<String, DocsumField> fields() { return fields; }

    /** Returns whether this summary contains one or more dynamic fields */
    public boolean isDynamic() { return dynamic; }

    /**
     * Returns the given slime value as the type specified in this, or null if the type is not known.
     * Even in a correctly configured system we may encounter field names for which we do not know the type,
     * in the time period when a configuration is changing and one node has received the new configuration and
     * another has not.
     */
    public Object convert(String fieldName, Inspector value) {
        DocsumField field = fields.get(fieldName);
        if (field == null || ! value.valid()) return null;
        return field.convert(value);
    }

    @Override
    public String toString() {
        return "docsum definition '" + name() + "'";
    }

}
