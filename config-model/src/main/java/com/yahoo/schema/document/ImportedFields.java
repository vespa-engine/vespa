// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.util.Collections;
import java.util.Map;

/**
 * A set of fields that are imported from concrete fields in referenced document types.
 *
 * @author geirst
 */
public class ImportedFields {

    private final Map<String, ImportedField> fields;

    public ImportedFields(Map<String, ImportedField> fields) {
        this.fields = fields;
    }

    public Map<String, ImportedField> fields() {
        return Collections.unmodifiableMap(fields);
    }
}
