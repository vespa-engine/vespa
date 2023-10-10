// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.messagebus.Message;

/**
 * <p>Implementation of VisitorDataHandler which invokes onDocument() for each
 * received document and onRemove() for each document id that was returned as
 * part of a remove entry. The latter only applies if the visitor was run with
 * visitRemoves enabled.</p>
 *
 * <p>NOTE: onDocument and onRemove may be called in a re-entrant manner, as
 * these run on top of a thread pool. Any mutation of shared state must be
 * appropriately synchronized.</p>
 */
public abstract class DumpVisitorDataHandler extends VisitorDataHandler {

    public DumpVisitorDataHandler() {
    }

    @Override
    public void onMessage(Message m, AckToken token) {
        if (m instanceof PutDocumentMessage) {
            PutDocumentMessage pm = (PutDocumentMessage)m;

            onDocument(pm.getDocumentPut().getDocument(), pm.getTimestamp());
        } else if (m instanceof RemoveDocumentMessage) {
            RemoveDocumentMessage rm = (RemoveDocumentMessage)m;
            onRemove(rm.getDocumentId());
        } else {
            throw new UnsupportedOperationException("Received unsupported message " + m.toString() + " to dump visitor data handler. This handler only accepts Put and Remove");
        }
        ack(token);
    }

    /**
     * Called when a document is received.
     *
     * May be called from multiple threads concurrently.
     *
     * @param doc The document found
     * @param timeStamp The time when the document was stored.
     */
    public abstract void onDocument(Document doc, long timeStamp);

    /**
     * Called when a remove is received.
     *
     * May be called from multiple threads concurrently.
     *
     * @param id The document id that was removed.
     */
    public abstract void onRemove(DocumentId id);

}
