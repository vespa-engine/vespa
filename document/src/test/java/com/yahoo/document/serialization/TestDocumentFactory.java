// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;

/**
 * Helper class for creating a document for a given document type.
 *
 * @author <a href="mailto:geirst@yahoo-inc.com">Geir Storli</a>
 */
public class TestDocumentFactory {

    private final DocumentTypeManager typeManager = new DocumentTypeManager();
    private final DocumentType docType;
    private final String defaultId;

    public TestDocumentFactory(DocumentType docType, String defaultId) {
        this.docType = docType;
        this.defaultId = defaultId;
        typeManager.register(docType);
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
