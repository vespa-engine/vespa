// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import java.util.ArrayList;

public class BatchDocumentUpdateReply extends WriteDocumentReply {

    private ArrayList<Boolean> documentsNotFound = new ArrayList<Boolean>();

    /**
     * Constructs a new reply with no content.
     */
    public BatchDocumentUpdateReply() {
        super(DocumentProtocol.REPLY_BATCHDOCUMENTUPDATE);
    }

    /**
     * If all documents to update are found, this vector will be empty. If
     * one or more documents are not found, this vector will have the size of
     * the initial number of updates, with entries set to true where the
     * corresponding update was not found.
     *
     * @return Vector containing indices of not found documents, or empty
     *   if all documents were found
     */
    public ArrayList<Boolean> getDocumentsNotFound() {
        return documentsNotFound;
    }
}
