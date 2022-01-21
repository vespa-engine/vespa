// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.documentmodel.DocumentTypeRepo;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.util.Map;
import java.util.TreeMap;

/**
* @author Thomas Gundersen
*/
public class SearchDefinitionBuilder {

    public Map<String, NewDocumentType> build(DocumentTypeRepo repo, ModelElement elem) {
        Map<String, NewDocumentType> docTypes = new TreeMap<>();

        if (elem != null) {
            for (ModelElement e : elem.subElements("document")) {
                String name = e.stringAttribute("type"); // Schema-guaranteed presence
                NewDocumentType documentType = repo.getDocumentType(name);
                if (documentType != null) {
                    docTypes.put(documentType.getName(), documentType);
                } else {
                    throw new IllegalArgumentException("Document type '" + name + "' not found in application package");
                }
            }
        }

        return docTypes;
    }
}
