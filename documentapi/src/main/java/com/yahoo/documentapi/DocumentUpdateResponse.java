// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.DocumentUpdate;
import com.yahoo.messagebus.Trace;

/**
 * The asynchronous response to a document update operation.
 * This is a <i>value object</i>.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentUpdateResponse extends Response {

    /** The document update of this response, if any */
    private final DocumentUpdate documentUpdate;

    /** Creates a successful response */
    public DocumentUpdateResponse(long requestId) {
        this(requestId, null);
    }

    /**
     * Creates a successful response containing a document update
     *
     * @param documentUpdate the DocumentUpdate to encapsulate in the Response
     */
    public DocumentUpdateResponse(long requestId, DocumentUpdate documentUpdate) {
        super(requestId);
        this.documentUpdate = documentUpdate;
    }

    /**
     * Creates a response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     * @param outcome     the outcome of this operation
     */
    public DocumentUpdateResponse(long requestId, String textMessage, Outcome outcome) {
        this(requestId, null, textMessage, outcome);
    }

    /**
     * Creates a response containing a textual message and/or a document update
     *
     * @param documentUpdate the DocumentUpdate to encapsulate in the Response
     * @param textMessage    the message to encapsulate in the Response
     * @param outcome        the outcome of this operation
     */
    public DocumentUpdateResponse(long requestId, DocumentUpdate documentUpdate, String textMessage, Outcome outcome) {
        this(requestId, documentUpdate, textMessage, outcome, null);
    }


    /**
     * Creates a response containing a textual message and/or a document update
     *
     * @param documentUpdate the DocumentUpdate to encapsulate in the Response
     * @param textMessage    the message to encapsulate in the Response
     * @param outcome        the outcome of this operation
     */
    public DocumentUpdateResponse(long requestId, DocumentUpdate documentUpdate, String textMessage, Outcome outcome, Trace trace) {
        super(requestId, textMessage, outcome, trace);
        this.documentUpdate = documentUpdate;
    }


    /**
     * Returns the document update of this response or null if there is none
     *
     * @return the DocumentUpdate, or null
     */
    public DocumentUpdate getDocumentUpdate() { return documentUpdate; }

    public int hashCode() {
        return super.hashCode() + (documentUpdate == null ? 0 : documentUpdate.hashCode());
    }

    public boolean equals(Object o) {
        if (!(o instanceof DocumentUpdateResponse)) {
            return false;
        }

        DocumentUpdateResponse docResp = (DocumentUpdateResponse) o;

        return super.equals(docResp) && ((documentUpdate == null && docResp.documentUpdate == null) || (
                documentUpdate != null && docResp.documentUpdate != null &&
                        documentUpdate.equals(docResp.documentUpdate)));
    }

    public String toString() {
        return "Update" + super.toString() + (documentUpdate == null ? "" : " " + documentUpdate);
    }

}
