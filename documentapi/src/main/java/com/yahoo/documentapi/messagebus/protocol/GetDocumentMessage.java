// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.DocumentId;
import com.yahoo.document.fieldset.DocumentOnly;

import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class GetDocumentMessage extends DocumentMessage {

    final static String DEFAULT_FIELD_SET = DocumentOnly.NAME;
    private DocumentId documentId = null;
    private String fieldSet = DEFAULT_FIELD_SET;

    /**
     * Constructs a new document get message.
     *
     * @param documentId The identifier of the document to get.
     */
    public GetDocumentMessage(DocumentId documentId) {
        setDocumentId(documentId);
    }

    /**
     * Constructs a new document get message.
     *
     * @param documentId The identifier of the document to get.
     * @param fieldSet Which fields to retrieve from the document
     */
    public GetDocumentMessage(DocumentId documentId, String fieldSet) {
        setDocumentId(documentId);
        this.fieldSet = fieldSet;
    }

    /**
     * Returns the identifier of the document to retrieve.
     *
     * @return The document id.
     */
    public DocumentId getDocumentId() {
        return documentId;
    }

    /**
     * Sets the identifier of the document to retrieve.
     *
     * @param documentId The document id to set.
     */
    public void setDocumentId(DocumentId documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Document id can not be null.");
        }
        this.documentId = documentId;
    }

    public String getFieldSet() {
        return fieldSet;
    }

    @Override
    public DocumentReply createReply() {
        return new GetDocumentReply();
    }

    @Override
    public int getApproxSize() {
        return super.getApproxSize() + 4 + documentId.toString().length();
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_GETDOCUMENT;
    }

}
