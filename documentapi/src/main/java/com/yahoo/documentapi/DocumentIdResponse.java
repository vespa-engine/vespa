// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.DocumentId;
import com.yahoo.messagebus.Trace;

/**
 * The asynchronous response to a document remove operation.
 * This is a <i>value object</i>.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentIdResponse extends Response {

    /** The document id of this response, if any */
    private final DocumentId documentId;

    /** Creates a successful response */
    public DocumentIdResponse(long requestId) {
        this(requestId, null);
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
     * Creates a response containing a textual message and/or a document id
     *
     * @param documentId  the DocumentId to encapsulate in the Response
     * @param textMessage the message to encapsulate in the Response
     * @param outcome     the outcome of the operation
     */
    public DocumentIdResponse(long requestId, DocumentId documentId, String textMessage, Outcome outcome) {
        this(requestId, documentId, textMessage, outcome, null);
    }


    /**
     * Creates a response containing a textual message and/or a document id
     *
     * @param documentId  the DocumentId to encapsulate in the Response
     * @param textMessage the message to encapsulate in the Response
     * @param outcome     the outcome of the operation
     */
    public DocumentIdResponse(long requestId, DocumentId documentId, String textMessage, Outcome outcome, Trace trace) {
        super(requestId, textMessage, outcome, null);
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
