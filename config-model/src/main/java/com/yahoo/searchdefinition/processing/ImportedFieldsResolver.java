// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.document.ImportedFields;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isArrayOfSimpleStruct;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isMapOfPrimitiveType;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isMapOfSimpleStruct;

/**
 * Iterates all imported fields from SD-parsing and validates and resolves them into concrete fields from referenced document types.
 *
 * @author geirst
 */
public class ImportedFieldsResolver extends Processor {

    private final Map<String, ImportedField> importedFields = new LinkedHashMap<>();
    private final Map<String, ImportedField> importedComplexFields = new LinkedHashMap<>();
    private final Optional<DocumentReferences> references;

    public ImportedFieldsResolver(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
        references = search.getDocument().getDocumentReferences();
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        search.temporaryImportedFields().get().fields().forEach((name, field) -> resolveImportedField(field, validate));
        search.setImportedFields(new ImportedFields(importedFields, importedComplexFields));
    }

    private void resolveImportedField(TemporaryImportedField importedField, boolean validate) {
        DocumentReference reference = validateDocumentReference(importedField);
        ImmutableSDField targetField = getTargetField(importedField, reference);
        if (isArrayOfSimpleStruct(targetField)) {
            resolveImportedArrayOfStructField(importedField, reference, targetField, validate);
        } else if (isMapOfSimpleStruct(targetField)) {
            resolveImportedMapOfStructField(importedField, reference, targetField, validate);
        } else if (isMapOfPrimitiveType(targetField)) {
            resolveImportedMapOfPrimitiveField(importedField, reference, targetField, validate);
        } else {
            resolveImportedNormalField(importedField, reference, targetField, validate);
        }
    }

    private void resolveImportedArrayOfStructField(TemporaryImportedField importedField, DocumentReference reference,
                                                   ImmutableSDField targetField, boolean validate) {
        resolveImportedNestedStructField(importedField, reference, targetField, validate);
        makeImportedComplexField(importedField, reference, targetField);
    }

    private void resolveImportedMapOfStructField(TemporaryImportedField importedField, DocumentReference reference,
                                                 ImmutableSDField targetField, boolean validate) {
        resolveImportedNestedField(importedField, reference, targetField.getStructField("key"), validate);
        resolveImportedNestedStructField(importedField, reference, targetField.getStructField("value"), validate);
        makeImportedComplexField(importedField, reference, targetField);
    }

    private void makeImportedComplexField(TemporaryImportedField importedField, DocumentReference reference,
                                                 ImmutableSDField targetField) {
        String name = importedField.fieldName();
        importedComplexFields.put(name, new ImportedField(name, reference, targetField));
    }

    private static String makeImportedNestedFieldName(TemporaryImportedField importedField, ImmutableSDField targetNestedField) {
        return importedField.fieldName() + targetNestedField.getName().substring(importedField.targetFieldName().length());
    }

    private boolean resolveImportedNestedField(TemporaryImportedField importedField, DocumentReference reference,
                                               ImmutableSDField targetNestedField, boolean requireAttribute) {
        Attribute attribute = targetNestedField.getAttributes().get(targetNestedField.getName());
        String importedNestedFieldName = makeImportedNestedFieldName(importedField, targetNestedField);
        if (attribute != null) {
            importedFields.put(importedNestedFieldName, new ImportedField(importedNestedFieldName, reference, targetNestedField));
        } else if (requireAttribute) {
            fail(importedField, importedNestedFieldName, targetFieldAsString(targetNestedField.getName(), reference) +
                    ": Is not an attribute field. Only attribute fields supported");
        }
        return attribute != null;
    }

    private void resolveImportedNestedStructField(TemporaryImportedField importedField, DocumentReference reference,
                                                  ImmutableSDField targetNestedField, boolean validate) {
        boolean foundAttribute = false;
        for (ImmutableSDField targetStructField : targetNestedField.getStructFields()) {
            if (resolveImportedNestedField(importedField, reference, targetStructField, false)) {
                foundAttribute = true;
            };
        }
        if (validate && !foundAttribute) {
            String importedNestedFieldName = makeImportedNestedFieldName(importedField, targetNestedField);
            fail(importedField, importedNestedFieldName, targetFieldAsString(targetNestedField.getName(), reference) +
                    ": Is not a struct containing an attribute field.");
        }
    }

    private void resolveImportedMapOfPrimitiveField(TemporaryImportedField importedField, DocumentReference reference,
                                                    ImmutableSDField targetField, boolean validate) {
        resolveImportedNestedField(importedField, reference, targetField.getStructField("key"), validate);
        resolveImportedNestedField(importedField, reference, targetField.getStructField("value"), validate);
        makeImportedComplexField(importedField, reference, targetField);
    }

    private void resolveImportedNormalField(TemporaryImportedField importedField, DocumentReference reference,
                                            ImmutableSDField targetField, boolean validate) {
        if (validate) {
            validateTargetField(importedField, targetField, reference);
        }
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

    private ImmutableSDField getTargetField(TemporaryImportedField importedField,
                                            DocumentReference reference) {
        String targetFieldName = importedField.targetFieldName();
        Search targetSearch = reference.targetSearch();
        ImmutableSDField targetField = targetSearch.getField(targetFieldName);
        if (targetField == null) {
            fail(importedField, targetFieldAsString(targetFieldName, reference) + ": Not found");
        }
        return targetField;
    }

    private void validateTargetField(TemporaryImportedField importedField,
                                                 ImmutableSDField targetField, DocumentReference reference) {
        if (!targetField.doesAttributing()) {
            fail(importedField, targetFieldAsString(targetField.getName(), reference) +
                    ": Is not an attribute field. Only attribute fields supported");
        } else if (targetField.doesIndexing()) {
            fail(importedField, targetFieldAsString(targetField.getName(), reference) +
                    ": Is an index field. Not supported");
        } else if (targetField.getDataType().equals(DataType.PREDICATE)) {
            fail(importedField, targetFieldAsString(targetField.getName(), reference) +
                    ": Is of type 'predicate'. Not supported");
        }
    }

    private static String targetFieldAsString(String targetFieldName, DocumentReference reference) {
        return "Field '" + targetFieldName + "' via reference field '" + reference.referenceField().getName() + "'";
    }

    private void fail(TemporaryImportedField importedField, String msg) {
        throw new IllegalArgumentException("For search '" + search.getName() + "', import field '" + importedField.fieldName() + "': " + msg);
    }

    private void fail(TemporaryImportedField importedField, String importedNestedFieldName, String msg) {
        throw new IllegalArgumentException("For search '" + search.getName() + "', import field '" +
                importedField.fieldName() + "' (nested to '" + importedNestedFieldName + "'): " + msg);
    }
}
