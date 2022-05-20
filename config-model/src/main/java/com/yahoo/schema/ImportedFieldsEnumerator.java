// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDDocumentType;

import java.util.Collection;

/**
 * Enumerates and emplaces a set of all imported fields into a SDDocumentType from
 * its corresponding Search instance.
 */
public class ImportedFieldsEnumerator {

    private final Collection<Schema> schemas;

    public ImportedFieldsEnumerator(Collection<Schema> schemas) {
        this.schemas = schemas;
    }

    public void enumerateImportedFields(SDDocumentType documentType) {
        var search = this.schemas.stream()
                                 .filter(s -> s.getDocument() != null)
                                 .filter(s -> s.getDocument().getName().equals(documentType.getName()))
                                 .findFirst();
        if (search.isEmpty()) {
            return; // No imported fields present.
        }
        search.get().temporaryImportedFields().ifPresent(documentType::setTemporaryImportedFields);
    }

}
