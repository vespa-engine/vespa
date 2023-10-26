// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;

/**
 * Helper class for creating a document for a given document type.
 *
 * @author geirst
 */
public class TestDocumentFactory {

    private final DocumentTypeManager typeManager;
    private final DocumentType docType;
    private final String defaultId;

    public TestDocumentFactory(DocumentType docType, String defaultId) {
        this.docType = docType;
        this.defaultId = defaultId;
        this.typeManager = new DocumentTypeManager();
        typeManager.register(docType);
    }

    /**
     * Utility constructor for setting up a factory with a preexisting document manager.
     * Does <em>not</em> automatically register docType in typeManager, but assumes it's already registered.
     */
    public TestDocumentFactory(DocumentTypeManager typeManager, DocumentType docType, String defaultId) {
        this.docType = docType;
        this.defaultId = defaultId;
        this.typeManager = typeManager;
    }

    public Document createDocument(String id) {
        return new Document(docType, id);
    }

    public Document createDocument() {
        return createDocument(defaultId);
    }

    public DocumentTypeManager typeManager() {
        return typeManager;
    }

}
