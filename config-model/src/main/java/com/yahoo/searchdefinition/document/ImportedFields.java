// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import java.util.Collections;
import java.util.Map;

/**
 * A set of fields that are imported from concrete fields in referenced document types.
 *
 * @author geirst
 */
public class ImportedFields {

    private final Map<String, ImportedField> fields;
    private final Map<String, ImportedField> complexFields;

    public ImportedFields(Map<String, ImportedField> fields, Map<String, ImportedField> complexFields) {
        this.fields = fields;
        this.complexFields = complexFields;
    }

    public Map<String, ImportedField> fields() {
        return Collections.unmodifiableMap(fields);
    }
    public Map<String, ImportedField> complexFields() { return Collections.unmodifiableMap(complexFields); }
}
