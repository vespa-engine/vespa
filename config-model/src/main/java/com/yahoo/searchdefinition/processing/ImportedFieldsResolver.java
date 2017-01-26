// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.document.ImportedFields;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Iterates all imported fields from SD-parsing and validates and resolves them into concrete fields from referenced document types.
 *
 * @author geirst
 */
public class ImportedFieldsResolver extends Processor {

    private final Map<String, ImportedField> importedFields = new LinkedHashMap<>();
    private final Optional<DocumentReferences> references;

    public ImportedFieldsResolver(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
        references = search.getDocument().getDocumentReferences();
    }

    @Override
    public void process() {
        search.temporaryImportedFields().get().fields().forEach((name, field) -> resolveImportedField(field));
        search.setImportedFields(new ImportedFields(importedFields));
    }

    private void resolveImportedField(TemporaryImportedField importedField) {
        DocumentReference reference = validateDocumentReference(importedField);
        SDField referencedField = validateReferencedField(importedField, reference);
        importedFields.put(importedField.aliasFieldName(), new ImportedField(importedField.aliasFieldName(), reference, referencedField));
    }

    private DocumentReference validateDocumentReference(TemporaryImportedField importedField) {
        String documentReferenceFieldName = importedField.documentReferenceFieldName();
        DocumentReference reference = references.get().referenceMap().get(documentReferenceFieldName);
        if (reference == null) {
            fail(importedField.aliasFieldName(), "Document reference field '" + documentReferenceFieldName + "' not found");
        }
        return reference;
    }

    private SDField validateReferencedField(TemporaryImportedField importedField, DocumentReference reference) {
        String foreignFieldName = importedField.foreignFieldName();
        SDField referencedField = reference.search().getField(foreignFieldName);
        if (referencedField == null) {
            fail(importedField.aliasFieldName(), "Field '" + foreignFieldName + "' via document reference field '" + reference.documentReferenceField().getName() + "' not found");
        }
        return referencedField;
    }

    private void fail(String importedFieldName, String msg) {
        throw new IllegalArgumentException("For search '" + search.getName() + "', import field '" + importedFieldName + "': Imported field is not valid. " + msg);
    }

}
