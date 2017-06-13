// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

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
        return documentsElement.subElements("document")
                .stream()
                .filter(GlobalDistributionBuilder::isGloballyDistributed)
                .map(GlobalDistributionBuilder::getDocumentName)
                .map(this::getDocumentType)
                .collect(toSet());
    }

    private static boolean isGloballyDistributed(ModelElement e) {
        return e.getBooleanAttribute("global", false);
    }

    private static String getDocumentName(ModelElement e) {
        return e.getStringAttribute("type");
    }

    private NewDocumentType getDocumentType(String name) {
        return documentDefinitions.get(name);
    }
}
