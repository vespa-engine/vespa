// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.DocumentOperation;

/**
 * Visitor response containing a document operation. All visitor responses have ack
 * tokens that must be acked.
 *
 * @author Arne H Juul
 */
public class DocumentOpVisitorResponse extends VisitorResponse {

    private DocumentOperation op;

    /**
     * Creates visitor response containing a document operation and an ack token.
     *
     * @param op the document operation
     * @param ack the ack token
     */
    public DocumentOpVisitorResponse(DocumentOperation op, AckToken ack) {
        super(ack);
        this.op = op;
    }

    /** @return the document operation */
    public DocumentOperation getDocumentOperation() { return op; }

}
