// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.Field;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Resolves all document references in the search definitions
 *
 * Iterates through all document fields having a {@link ReferenceDataType} and uses {@link ReferenceDataType#getTargetType()}
 * to determine the referenced document. This information is aggregated into a {@link DocumentReferences} object.
 *
 * @author bjorncs
 */
public class DocumentReferenceResolver {

    private final Map<String, Search> searchMapping;

    public DocumentReferenceResolver(List<Search> searchDefinitions) {
        this.searchMapping = createDocumentNameToSearchMapping(searchDefinitions);
    }

    public void resolveReferences(SDDocumentType documentType) {
        DocumentReferences references = new DocumentReferences(createFieldToDocumentReferenceMapping(documentType));
        documentType.setDocumentReferences(references);
    }

    private Map<String, DocumentReference> createFieldToDocumentReferenceMapping(SDDocumentType documentType) {
        return fieldStream(documentType)
                .filter(field -> field.getDataType() instanceof ReferenceDataType)
                .collect(toMap(Field::getName, this::createDocumentReference));
    }

    private DocumentReference createDocumentReference(Field field) {
        if (!isAttribute(field)) {
            throw new IllegalArgumentException(
                    String.format(
                            "The field '%s' is an invalid document reference. The field must be an attribute.",
                            field.getName()));
        }
        ReferenceDataType reference = (ReferenceDataType) field.getDataType();
        String targetDocumentName = getTargetDocumentName(reference);
        Search search = searchMapping.get(targetDocumentName);
        if (search == null) {
            throw new IllegalArgumentException(
                    String.format("Invalid document reference '%s': " +
                                  "Could not find document type '%s'", field.getName(), targetDocumentName));
        }
        return new DocumentReference(field, search);
    }

    private static boolean isAttribute(Field field) {
        SDField sdField = (SDField) field; // Ugly, but SDDocumentType only expose the fields as the super class Field
        return sdField.doesAttributing();
    }

    private static Map<String, Search> createDocumentNameToSearchMapping(List<Search> searchDefintions) {
        return searchDefintions.stream()
                .filter(search -> search.getDocument() != null)
                .collect(toMap(search -> search.getDocument().getName(), identity()));
    }

    private static Stream<Field> fieldStream(SDDocumentType documentType) {
        return documentType.getDocumentType().getFields().stream();
    }

    private static String getTargetDocumentName(ReferenceDataType reference) {
        return reference.getTargetType().getName();
    }

}
