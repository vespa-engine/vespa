// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        SDField targetField = validateTargetField(importedField, reference);
        importedFields.put(importedField.fieldName(), new ImportedField(importedField.fieldName(), reference, targetField));
    }

    private DocumentReference validateDocumentReference(TemporaryImportedField importedField) {
        String referenceFieldName = importedField.referenceFieldName();
        DocumentReference reference = references.get().referenceMap().get(referenceFieldName);
        if (reference == null) {
            fail(importedField, "Reference field '" + referenceFieldName + "' not found");
        }
        return reference;
    }

    private SDField validateTargetField(TemporaryImportedField importedField, DocumentReference reference) {
        String targetFieldName = importedField.targetFieldName();
        Search targetSearch = reference.targetSearch();
        if (isImportedField(targetSearch, targetFieldName)) {
            fail(importedField, targetFieldAsString(targetFieldName, reference) + ": Is an imported field. Not supported");
        }
        SDField targetField = targetSearch.getConcreteField(targetFieldName);
        if (targetField == null) {
            fail(importedField, targetFieldAsString(targetFieldName, reference) + ": Not found");
        } else if (!targetField.doesAttributing()) {
            fail(importedField, targetFieldAsString(targetFieldName, reference) + ": Is not an attribute field. Only attribute fields supported");
        } else if (targetField.doesIndexing()) {
            fail(importedField, targetFieldAsString(targetFieldName, reference) + ": Is an index field. Not supported");
        }
        return targetField;
    }

    private static boolean isImportedField(Search targetSearch, String targetFieldName) {
        return targetSearch.importedFields().isPresent() && targetSearch.importedFields().get().fields().containsKey(targetFieldName);
    }

    private static String targetFieldAsString(String targetFieldName, DocumentReference reference) {
        return "Field '" + targetFieldName + "' via reference field '" + reference.referenceField().getName() + "'";
    }

    private void fail(TemporaryImportedField importedField, String msg) {
        throw new IllegalArgumentException("For search '" + search.getName() + "', import field '" + importedField.fieldName() + "': " + msg);
    }

}
