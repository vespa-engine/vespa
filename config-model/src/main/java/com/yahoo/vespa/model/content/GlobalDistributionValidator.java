// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * Performs the following validations:
 *  - Verify that all global documents have required redundancy
 *  - Verify that all referred documents are present in services.xml
 *  - Verify that all referred documents are global
 *
 * @author bjorncs
 */
public class GlobalDistributionValidator {

    public void validate(Map<String, NewDocumentType> documentDefinitions,
                         Set<NewDocumentType> globallyDistributedDocuments,
                         Redundancy redundancy,
                         boolean enableMultipleBucketSpaces) {
        if (!enableMultipleBucketSpaces) {
            verifyGlobalDocumentsHaveRequiredRedundancy(globallyDistributedDocuments, redundancy);
        }
        verifySearchableCopiesIsSameAsRedundancy(globallyDistributedDocuments, redundancy);
        verifyReferredDocumentsArePresent(documentDefinitions);
        verifyReferredDocumentsAreGlobal(documentDefinitions, globallyDistributedDocuments);
    }

    private static void verifyGlobalDocumentsHaveRequiredRedundancy(Set<NewDocumentType> globallyDistributedDocuments,
                                                                    Redundancy redundancy) {
        if (!globallyDistributedDocuments.isEmpty() && !redundancy.isEffectivelyGloballyDistributed()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The following document types are marked as global, " +
                                    "but do not have high enough redundancy to make the documents globally distributed: %s. " +
                                    "Redundancy is %d, expected %d.",
                            asPrintableString(toDocumentNameStream(globallyDistributedDocuments)),
                            redundancy.effectiveFinalRedundancy(),
                            redundancy.totalNodes()));
        }
    }

    private static void verifySearchableCopiesIsSameAsRedundancy(Set<NewDocumentType> globallyDistributedDocuments,
                                                                 Redundancy redundancy) {
        if (!globallyDistributedDocuments.isEmpty() &&
                redundancy.effectiveReadyCopies() != redundancy.effectiveFinalRedundancy()) {
            throw new IllegalArgumentException(
                    String.format(
                            "The following document types have the number of searchable copies less than redundancy: %s. " +
                                    "Searchable copies is %d, while redundancy is %d.",
                            asPrintableString(toDocumentNameStream(globallyDistributedDocuments)),
                            redundancy.effectiveReadyCopies(),
                            redundancy.effectiveFinalRedundancy()));
        }
    }

    private static void verifyReferredDocumentsArePresent(Map<String, NewDocumentType> documentDefinitions) {
        Set<NewDocumentType.Name> unknowDocuments = getReferencedDocuments(documentDefinitions)
                .filter(name -> !documentDefinitions.containsKey(name.toString()))
                .collect(toSet());
        if (!unknowDocuments.isEmpty()) {
            throw new IllegalArgumentException("The following document types are referenced from other documents, " +
                    "but are not listed in services.xml: " + asPrintableString(unknowDocuments.stream()));
        }
    }

    private static void verifyReferredDocumentsAreGlobal(Map<String, NewDocumentType> documentDefinitions,
                                                         Set<NewDocumentType> globallyDistributedDocuments) {
        Set<NewDocumentType> nonGlobalReferencedDocuments = getReferencedDocuments(documentDefinitions)
                .map(name -> documentDefinitions.get(name.toString()))
                .filter(documentType -> !globallyDistributedDocuments.contains(documentType))
                .collect(toSet());
        if (!nonGlobalReferencedDocuments.isEmpty()) {
            throw new IllegalArgumentException("The following document types are referenced from other documents, " +
                    "but are not globally distributed: " + asPrintableString(toDocumentNameStream(nonGlobalReferencedDocuments)));
        }
    }

    private static Stream<NewDocumentType.Name> getReferencedDocuments(Map<String, NewDocumentType> documentDefinitions) {
        return documentDefinitions.values().stream()
                .map(NewDocumentType::getDocumentReferences)
                .flatMap(Set::stream);
    }

    private static Stream<NewDocumentType.Name> toDocumentNameStream(Set<NewDocumentType> globallyDistributedDocuments) {
        return globallyDistributedDocuments.stream().map(NewDocumentType::getFullName);
    }

    private static String asPrintableString(Stream<NewDocumentType.Name> documentTypes) {
        return documentTypes.map(name -> "'" + name + "'").collect(joining(", "));
    }
}
