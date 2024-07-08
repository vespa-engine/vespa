// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.api.annotations.Beta;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.TestAndSetCondition;

import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class RemoveDocumentMessage extends TestAndSetMessage {
    private DocumentRemove remove = null;
    private long persistedTimestamp = 0;

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

    /**
     * <p>Set the timestamp of the last known tombstone for this document.</p>
     *
     * <p>This is normally only invoked by the backends as part of visiting.</p>
     */
    @Beta
    public void setPersistedTimestamp(long time) {
        this.persistedTimestamp = time;
    }

    /**
     * <p>When a visitor client receives a Remove as part of the visiting operation, this
     * timestamp represents the wall clock time in microseconds(*) of the tombstone's
     * creation (i.e. the highest known time the original document was removed).</p>
     *
     * <p>If zero, the sending content node is too old to support this feature.</p>
     *
     * <p>This value is not guaranteed to be linearizable. During e.g. network partitions this
     * value might not represent the latest acknowledged operation for the document.</p>
     *
     * <p>Unsupported (and ignored) for Removes sent by the client during feeding.</p>
     *
     * <p>(*) (wall clock seconds since UTC epoch * 1M) + synthetic intra-second microsecond counter.</p>
     */
    @Beta
    public long getPersistedTimestamp() {
        return this.persistedTimestamp;
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
