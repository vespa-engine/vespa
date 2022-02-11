// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Determines the set of document types that are configured to be globally distributed.
 *
 * @author bjorncs
 */
public class GlobalDistributionBuilder {

    private final Map<String, NewDocumentType> documentDefinitions;

    public GlobalDistributionBuilder(Map<String, NewDocumentType> documentDefinitions) {
        this.documentDefinitions = Collections.unmodifiableMap(documentDefinitions);
    }

    public Set<NewDocumentType> build(ModelElement documentsElement) {
        if (documentsElement == null || documentsElement.subElements("document").isEmpty())
            return Collections.emptySet();

        return documentsElement.subElements("document")
                .stream()
                .filter(GlobalDistributionBuilder::isGloballyDistributed)
                .map(GlobalDistributionBuilder::getDocumentName)
                .map(this::getDocumentType)
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

    private static boolean isGloballyDistributed(ModelElement e) {
        return e.booleanAttribute("global", false);
    }

    private static String getDocumentName(ModelElement e) {
        return e.stringAttribute("type");
    }

    private NewDocumentType getDocumentType(String name) {
        return documentDefinitions.get(name);
    }
}
