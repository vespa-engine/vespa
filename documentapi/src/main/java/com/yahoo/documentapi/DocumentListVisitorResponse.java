// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.vdslib.DocumentList;

/**
 * Visitor response containing a document list. All visitor responses have ack
 * tokens that must be acked.
 *
 * @author <a href="mailto:humbe@yahoo-inc.com">H&aring;kon Humberset</a>
 */
public class DocumentListVisitorResponse extends VisitorResponse {
    private DocumentList documents;

    /**
     * Creates visitor response containing a document list and an ack token.
     *
     * @param docs the document list
     * @param ack the ack token
     */
    public DocumentListVisitorResponse(DocumentList docs, AckToken ack) {
        super(ack);
        documents = docs;
    }

    /** @return the document list */
    public DocumentList getDocumentList() { return documents; }
}
