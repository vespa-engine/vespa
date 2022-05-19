// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDDocumentType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * Validates that there are no cycles between document types (exception: self-reference is allowed).
 * Example: if document B inherits A, then A cannot have a document reference to B.
 *
 * @author bjorncs
 */
public class DocumentGraphValidator {

    public void validateDocumentGraph(List<SDDocumentType> documents) {
        for (SDDocumentType document : documents) {
            validateRoot(document);
        }
    }

    private static void validateRoot(SDDocumentType root) {
        validateChildren(root, root);
    }

    private static void validateChildren(SDDocumentType root, SDDocumentType currentDocument) {
        try {
            currentDocument.getDocumentReferences().get()
                    .forEach(entry -> {
                        SDDocumentType referencedDocument = entry.getValue().targetSearch().getDocument();
                        validateDocument(root, referencedDocument);
                    });
            currentDocument.getInheritedTypes()
                    .forEach(inheritedDocument -> {
                        if (!isRootDocument(inheritedDocument)) {
                            validateDocument(root, inheritedDocument);
                        }
                    });
        } catch (DocumentGraphException e) {
            e.addParentDocument(currentDocument);
            throw e;
        }
    }

    private static void validateDocument(SDDocumentType root, SDDocumentType currentDocument) {
        if (root.equals(currentDocument)) {
            throw new DocumentGraphException(currentDocument);
        }
        validateChildren(root, currentDocument);
    }

    private static boolean isRootDocument(SDDocumentType doc) {
        return doc.getName().equals("document");
    }

    public static class DocumentGraphException extends IllegalArgumentException {
        private final Deque<SDDocumentType> deque = new ArrayDeque<>();

        public DocumentGraphException(SDDocumentType document) {
            deque.addLast(document);
        }

        public void addParentDocument(SDDocumentType document) {
            deque.addFirst(document);
        }

        @Override
        public String getMessage() {
            return deque.stream()
                    .map(SDDocumentType::getName)
                    .collect(joining("->", "Document dependency cycle detected: ", "."));
        }
    }

}
