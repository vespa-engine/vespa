// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.TestAndSetCondition;

import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class RemoveDocumentMessage extends TestAndSetMessage {
    private DocumentRemove remove = null;

    /**
     * Constructs a new message for deserialization.
     */
    RemoveDocumentMessage() {
        // empty
    }

    /**
     * Constructs a new document remove message.
     *
     * @param documentId The identifier of the document to remove.
     */
    public RemoveDocumentMessage(DocumentId documentId) {
        remove = new DocumentRemove(documentId);
    }

    /**
     * Constructs a new document remove message.
     *
     * @param remove The DocumentRemove operation to perform
     */
    public RemoveDocumentMessage(DocumentRemove remove) {
        this.remove = remove;
    }

    /**
     * Returns the identifier of the document to remove.
     *
     * @return The document id.
     */
    public DocumentId getDocumentId() {
        return remove.getId();
    }

    /**
     * Sets the identifier of the document to remove.
     *
     * @param documentId The document id to set.
     */
    public void setDocumentId(DocumentId documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Document id can not be null.");
        }

        remove = new DocumentRemove(documentId);
    }

    @Override
    public DocumentReply createReply() {
        return new RemoveDocumentReply();
    }

    @Override
    public int getApproxSize() {
        return super.getApproxSize() + 4 + remove.getId().toString().length();
    }

    @Override
    public boolean hasSequenceId() {
        return true;
    }

    @Override
    public long getSequenceId() {
        return Arrays.hashCode(remove.getId().getGlobalId());
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_REMOVEDOCUMENT;
    }

    @Override
    public void setCondition(TestAndSetCondition condition) {
        remove.setCondition(condition);
    }

    @Override
    public TestAndSetCondition getCondition() {
        return remove.getCondition();
    }

    public DocumentRemove getDocumentRemove() {
        return remove;
    }
}
