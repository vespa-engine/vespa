// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.Field;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Resolves all document references in the search definitions
 *
 * Iterates through all document fields having a {@link NewDocumentReferenceDataType} and uses {@link NewDocumentReferenceDataType#getTargetTypeName()}
 * to determine the referenced document. This information is aggregated into a {@link DocumentReferences} object.
 *
 * @author bjorncs
 */
public class DocumentReferenceResolver {

    private final Map<String, Schema> schemaMapping;

    public DocumentReferenceResolver(Collection<Schema> schemas) {
        this.schemaMapping = createDocumentNameToSearchMapping(schemas);
    }

    public void resolveReferences(SDDocumentType documentType) {
        var references = new DocumentReferences(createFieldToDocumentReferenceMapping(documentType));
        documentType.setDocumentReferences(references);
    }

    public void resolveInheritedReferences(SDDocumentType documentType) {
        resolveInheritedReferencesRecursive(documentType, documentType.getInheritedTypes());
    }

    private void resolveInheritedReferencesRecursive(SDDocumentType documentType,
                                                     Collection<SDDocumentType> inheritedTypes) {
        for (var inheritedType : inheritedTypes) {
            documentType.getDocumentReferences().get().mergeFrom(inheritedType.getDocumentReferences().get());
        }
        for (var inheritedType : inheritedTypes) {
            resolveInheritedReferencesRecursive(documentType, inheritedType.getInheritedTypes());
        }
    }

    private Map<String, DocumentReference> createFieldToDocumentReferenceMapping(SDDocumentType documentType) {
        return fieldStream(documentType)
                .filter(field -> field.getDataType() instanceof NewDocumentReferenceDataType)
                .collect(toMap(Field::getName, this::createDocumentReference));
    }

    private DocumentReference createDocumentReference(Field field) {
        if (!isAttribute(field)) {
            throw new IllegalArgumentException(
                    String.format(
                            "The field '%s' is an invalid document reference. The field must be an attribute.",
                            field.getName()));
        }
        NewDocumentReferenceDataType reference = (NewDocumentReferenceDataType) field.getDataType();
        String targetDocumentName = getTargetDocumentName(reference);
        Schema schema = schemaMapping.get(targetDocumentName);
        if (schema == null) {
            throw new IllegalArgumentException(
                    String.format("Invalid document reference '%s': " +
                                  "Could not find document type '%s'", field.getName(), targetDocumentName));
        }
        return new DocumentReference(field, schema);
    }

    private static boolean isAttribute(Field field) {
        SDField sdField = (SDField) field; // Ugly, but SDDocumentType only expose the fields as the super class Field
        return sdField.doesAttributing();
    }

    private static Map<String, Schema> createDocumentNameToSearchMapping(Collection<Schema> schemaDefintions) {
        return schemaDefintions.stream()
                               .filter(search -> search.getDocument() != null)
                               .collect(toMap(search -> search.getDocument().getName(), identity()));
    }

    private static Stream<Field> fieldStream(SDDocumentType documentType) {
        return documentType.getDocumentType().getFields().stream();
    }

    private static String getTargetDocumentName(NewDocumentReferenceDataType reference) {
        return reference.getTargetTypeName();
    }

}
