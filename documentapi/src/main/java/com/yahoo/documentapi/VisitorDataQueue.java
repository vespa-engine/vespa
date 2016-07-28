// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.BucketId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.documentapi.messagebus.protocol.DocumentListEntry;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.messagebus.Message;
import com.yahoo.vdslib.DocumentList;
import com.yahoo.vdslib.Entry;

import java.util.LinkedList;
import java.util.List;


/**
 * A visitor data handler that queues up documents in visitor responses and
 * implements the <code>getNext</code> methods, thus implementing the polling
 * API defined in VisitorDataHandler.
 * <p>
 * Visitor responses containing document lists should be polled for with the
 * <code>getNext</code> methods and need to be acked when processed for
 * visiting not to halt. The class is thread safe.
 *
 * @author <a href="mailto:humbe@yahoo-inc.com">Håkon Humberset</a>
 */
public class VisitorDataQueue extends VisitorDataHandler {

    final LinkedList<VisitorResponse> pendingResponses = new LinkedList<VisitorResponse>();

    /** Creates a new visitor data queue. */
    public VisitorDataQueue() {
    }

    // Inherit doc from VisitorDataHandler
    @Override
    public void reset() {
        super.reset();
        synchronized (pendingResponses) {
            pendingResponses.clear();
        }
    }

    private void appendSingleOpToPendingList(final DocumentOperation op, final AckToken token) {
        final DocumentList docList = DocumentList.create(Entry.create(op));
        final DocumentListVisitorResponse response = new DocumentListVisitorResponse(docList, token);
        synchronized (pendingResponses) {
            pendingResponses.add(response);
            pendingResponses.notifyAll();
        }
    }

    @Override
    public void onMessage(Message m, AckToken token) {
        if (m instanceof PutDocumentMessage) {
            appendSingleOpToPendingList(((PutDocumentMessage)m).getDocumentPut(), token);
        } else if (m instanceof RemoveDocumentMessage) {
            appendSingleOpToPendingList(((RemoveDocumentMessage)m).getDocumentRemove(), token);
        } else {
            throw new UnsupportedOperationException(
                    String.format("Expected put/remove message, got '%s' of type %s",
                                  m.toString(), m.getClass().toString()));
        }
    }

    /**
     * @deprecated This method is no longer called by the visitor subsystem. See onMessage instead.
     */
    @Deprecated
    public void onDocuments(DocumentList docs, AckToken token) {
        synchronized (pendingResponses) {
            pendingResponses.add(new DocumentListVisitorResponse(docs, token));
            pendingResponses.notifyAll();
        }
    }

    // Inherit doc from VisitorDataHandler
    @Override
    public VisitorResponse getNext() {
        synchronized (pendingResponses) {
            return (pendingResponses.isEmpty()
                        ? null : pendingResponses.removeFirst());
        }
    }

    // Inherit doc from VisitorDataHandler
    @Override
    public VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException {
        synchronized (pendingResponses) {
            if (pendingResponses.isEmpty()) {
                if (timeoutMilliseconds == 0) {
                    while (pendingResponses.isEmpty()) {
                        pendingResponses.wait();
                    }
                } else {
                    pendingResponses.wait(timeoutMilliseconds);
                }
            }
            return (pendingResponses.isEmpty()
                        ? null : pendingResponses.removeFirst());
        }
    }

    @Override
    public void onDone() {
        synchronized (pendingResponses) {
            pendingResponses.notifyAll();
        }
    }
}
