// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class GlobalDistributionBuilderTest {

    private static final NewDocumentType NON_GLOBAL_EXPLICIT = new NewDocumentType(new NewDocumentType.Name("non-global-explicit"));
    private static final NewDocumentType NON_GLOBAL_IMPLICIT = new NewDocumentType(new NewDocumentType.Name("non-global-implicit"));
    private static final NewDocumentType GLOBAL_1 = new NewDocumentType(new NewDocumentType.Name("global-1"));
    private static final NewDocumentType GLOBAL_2 = new NewDocumentType(new NewDocumentType.Name("global-2"));

    @Test
    void global_documents_are_identified() {
        GlobalDistributionBuilder builder = new GlobalDistributionBuilder(createDocumentDefinitions());
        String documentsElement =
                "<documents>" +
                        "  <document type=\"" + NON_GLOBAL_EXPLICIT.getName() + "\" global=\"false\"/>" +
                        "  <document type=\"" + GLOBAL_1.getName() + "\" global=\"true\"/>" +
                        "  <document type=\"" + NON_GLOBAL_IMPLICIT.getName() + "\"/>" +
                        "  <document type=\"" + GLOBAL_2.getName() + "\" global=\"true\"/>" +
                        "</documents>";

        Set<NewDocumentType> expectedResult = new HashSet<>(Arrays.asList(GLOBAL_1, GLOBAL_2));
        Set<NewDocumentType> actualResult = builder.build(new ModelElement(XML.getDocument(documentsElement).getDocumentElement()));
        assertEquals(expectedResult, actualResult);
    }

    private static Map<String, NewDocumentType> createDocumentDefinitions() {
        Map<String, NewDocumentType> documentTypes = new HashMap<>();
        addType(documentTypes, NON_GLOBAL_EXPLICIT);
        addType(documentTypes, GLOBAL_1);
        addType(documentTypes, NON_GLOBAL_IMPLICIT);
        addType(documentTypes, GLOBAL_2);
        return documentTypes;
    }

    private static void addType(Map<String, NewDocumentType> documentTypes, NewDocumentType documentType) {
        documentTypes.put(documentType.getName(), documentType);
    }

}
