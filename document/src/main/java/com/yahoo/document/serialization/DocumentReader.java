// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;

/**
 * This interface is used to implement custom deserialization of document updates.
 *
 * @author Ravi Sharma
 * @author baldersheim
 */
public interface DocumentReader {

    /**
     * Read a document
     *
     * @param document - document to be read
     */
    void read(Document document);

    DocumentId readDocumentId();
    DocumentType readDocumentType();

}
