// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.DocumentId;

/**
 * The asynchronous response to a document remove operation.
 * This is a <i>value object</i>.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentIdResponse extends Response {

    /** The document id of this response, if any */
    private final DocumentId documentId;

    /** Creates a successful response */
    public DocumentIdResponse(long requestId) {
        super(requestId);
        documentId = null;
    }

    /**
     * Creates a successful response containing a document id
     *
     * @param documentId the DocumentId to encapsulate in the Response
     */
    public DocumentIdResponse(long requestId, DocumentId documentId) {
        super(requestId);
        this.documentId = documentId;
    }

    /**
     * Creates a response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     * @param success     true if the response represents a successful call
     */
    public DocumentIdResponse(long requestId, String textMessage, boolean success) {
        super(requestId, textMessage, success);
        documentId = null;
    }

    /**
     * Creates a response containing a textual message and/or a document id
     *
     * @param documentId  the DocumentId to encapsulate in the Response
     * @param textMessage the message to encapsulate in the Response
     * @param success     true if the response represents a successful call
     */
    public DocumentIdResponse(long requestId, DocumentId documentId, String textMessage, boolean success) {
        super(requestId, textMessage, success);
        this.documentId = documentId;
    }


    /**
     * Returns the document id of this response, or null if there is none
     *
     * @return the DocumentId, or null
     */
    public DocumentId getDocumentId() { return documentId; }

    public int hashCode() {
        return super.hashCode() + (documentId == null ? 0 : documentId.hashCode());
    }

    public boolean equals(Object o) {
        if (!(o instanceof DocumentIdResponse)) {
            return false;
        }

        DocumentIdResponse docResp = (DocumentIdResponse) o;

        return super.equals(docResp) && ((documentId == null && docResp.documentId == null) ||
                (documentId != null && docResp.documentId != null && documentId.equals(docResp.documentId)));
    }

    public String toString() {
        return "DocumentId" + super.toString() + (documentId == null ? "" : " " + documentId);
    }

}
