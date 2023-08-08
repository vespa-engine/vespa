// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.schema.DocumentReference;
import com.yahoo.schema.DocumentReferences;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.GeoPos;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.ImportedComplexField;
import com.yahoo.schema.document.ImportedField;
import com.yahoo.schema.document.ImportedFields;
import com.yahoo.schema.document.ImportedSimpleField;
import com.yahoo.schema.document.TemporaryImportedField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.schema.document.ComplexAttributeFieldUtils.isArrayOfSimpleStruct;
import static com.yahoo.schema.document.ComplexAttributeFieldUtils.isMapOfPrimitiveType;
import static com.yahoo.schema.document.ComplexAttributeFieldUtils.isMapOfSimpleStruct;

/**
 * Iterates all imported fields from schema parsing and validates and resolves them into concrete fields from referenced document types.
 *
 * @author geirst
 */
public class ImportedFieldsResolver extends Processor {

    private final Map<String, ImportedField> importedFields = new LinkedHashMap<>();
    private final Optional<DocumentReferences> references;

    public ImportedFieldsResolver(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
        references = schema.getDocument().getDocumentReferences();
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        schema.temporaryImportedFields().get().fields().forEach((name, field) -> resolveImportedField(field, validate));
        schema.setImportedFields(new ImportedFields(importedFields));
    }

    private void resolveImportedField(TemporaryImportedField importedField, boolean validate) {
        DocumentReference reference = validateDocumentReference(importedField);
        ImmutableSDField targetField = getTargetField(importedField, reference);
        if (GeoPos.isAnyPos(targetField)) {
            resolveImportedPositionField(importedField, reference, targetField, validate);
        } else if (isArrayOfSimpleStruct(targetField)) {
            resolveImportedArrayOfStructField(importedField, reference, targetField, validate);
        } else if (isMapOfSimpleStruct(targetField)) {
            resolveImportedMapOfStructField(importedField, reference, targetField, validate);
        } else if (isMapOfPrimitiveType(targetField)) {
            resolveImportedMapOfPrimitiveField(importedField, reference, targetField, validate);
        } else {
            resolveImportedNormalField(importedField, reference, targetField, validate);
        }
    }

    private void resolveImportedPositionField(TemporaryImportedField importedField, DocumentReference reference,
                                              ImmutableSDField targetField, boolean validate) {
        TemporaryImportedField importedZCurveField = new TemporaryImportedField(PositionDataType.getZCurveFieldName(importedField.fieldName()),
                reference.referenceField().getName(), PositionDataType.getZCurveFieldName(targetField.getName()));
        ImmutableSDField targetZCurveField = getTargetField(importedZCurveField, reference);
        resolveImportedNormalField(importedZCurveField, reference, targetZCurveField, validate);
        ImportedComplexField importedStructField = new ImportedComplexField(importedField.fieldName(), reference, targetField);
        registerImportedField(importedField, null, importedStructField);
    }

    private void resolveImportedArrayOfStructField(TemporaryImportedField importedField, DocumentReference reference,
                                                   ImmutableSDField targetField, boolean validate) {
        ImportedComplexField importedStructField = new ImportedComplexField(importedField.fieldName(), reference, targetField);
        resolveImportedNestedStructField(importedField, reference, importedStructField, targetField, validate);
        registerImportedField(importedField, null, importedStructField);
    }

    private void resolveImportedMapOfStructField(TemporaryImportedField importedField, DocumentReference reference,
                                                 ImmutableSDField targetField, boolean validate) {
        ImportedComplexField importedMapField = new ImportedComplexField(importedField.fieldName(), reference, targetField);
        ImportedComplexField importedStructField = new ImportedComplexField(importedField.fieldName() + ".value", reference, targetField.getStructField("value"));
        importedMapField.addNestedField(importedStructField);
        resolveImportedNestedField(importedField, reference, importedMapField, targetField.getStructField("key"), validate);
        resolveImportedNestedStructField(importedField, reference, importedStructField, importedStructField.targetField(), validate);
        registerImportedField(importedField, null, importedMapField);
    }

    private void makeImportedNormalField(TemporaryImportedField importedField, ImportedComplexField owner, String name, DocumentReference reference, ImmutableSDField targetField) {
        ImportedField importedSimpleField = new ImportedSimpleField(name, reference, targetField);
        registerImportedField(importedField, owner, importedSimpleField);
    }

    private void registerImportedField(TemporaryImportedField temporaryImportedField, ImportedComplexField owner, ImportedField importedField) {
        if (owner != null) {
            owner.addNestedField(importedField);
        } else {
            if (importedFields.get(importedField.fieldName()) != null) {
                fail(temporaryImportedField, importedField.fieldName(), targetFieldAsString(importedField.targetField().getName(), importedField.reference()) + ": Field already imported");
            }
            importedFields.put(importedField.fieldName(), importedField);
        }
    }

    private static String makeImportedNestedFieldName(TemporaryImportedField importedField, ImmutableSDField targetNestedField) {
        return importedField.fieldName() + targetNestedField.getName().substring(importedField.targetFieldName().length());
    }

    private boolean resolveImportedNestedField(TemporaryImportedField importedField, DocumentReference reference,
                                               ImportedComplexField owner, ImmutableSDField targetNestedField, boolean requireAttribute) {
        Attribute attribute = targetNestedField.getAttribute();
        String importedNestedFieldName = makeImportedNestedFieldName(importedField, targetNestedField);
        if (attribute != null) {
            makeImportedNormalField(importedField, owner, importedNestedFieldName, reference, targetNestedField);
        } else if (requireAttribute) {
            fail(importedField, importedNestedFieldName, targetFieldAsString(targetNestedField.getName(), reference) +
                    ": Is not an attribute field. Only attribute fields supported");
        }
        return attribute != null;
    }

    private void resolveImportedNestedStructField(TemporaryImportedField importedField, DocumentReference reference,
                                                  ImportedComplexField ownerField, ImmutableSDField targetNestedField, boolean validate) {
        boolean foundAttribute = false;
        for (ImmutableSDField targetStructField : targetNestedField.getStructFields()) {
            if (resolveImportedNestedField(importedField, reference, ownerField, targetStructField, false)) {
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
        ImportedComplexField importedMapField = new ImportedComplexField(importedField.fieldName(), reference, targetField);
        resolveImportedNestedField(importedField, reference, importedMapField, targetField.getStructField("key"), validate);
        resolveImportedNestedField(importedField, reference, importedMapField, targetField.getStructField("value"), validate);
        registerImportedField(importedField, null, importedMapField);
    }

    private void resolveImportedNormalField(TemporaryImportedField importedField, DocumentReference reference,
                                            ImmutableSDField targetField, boolean validate) {
        if (validate) {
            validateTargetField(importedField, targetField, reference);
        }
        makeImportedNormalField(importedField, null, importedField.fieldName(), reference, targetField);
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
        Schema targetSchema = reference.targetSearch();
        ImmutableSDField targetField = targetSchema.getField(targetFieldName);
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
        throw new IllegalArgumentException("For " + schema + ", import field '" +
                                           importedField.fieldName() + "': " + msg);
    }

    private void fail(TemporaryImportedField importedField, String importedNestedFieldName, String msg) {
        if (importedField.fieldName().equals(importedNestedFieldName)) {
            fail(importedField, msg);
        }
        throw new IllegalArgumentException("For " + schema + ", import field '" +
                                           importedField.fieldName() + "' (nested to '" + importedNestedFieldName + "'): " + msg);
    }
}
