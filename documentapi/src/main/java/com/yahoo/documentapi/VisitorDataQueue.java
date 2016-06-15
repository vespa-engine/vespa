// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.BucketId;
import com.yahoo.documentapi.messagebus.protocol.DocumentListEntry;
import com.yahoo.messagebus.Message;
import com.yahoo.vdslib.DocumentList;

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
    public void reset() {
        super.reset();
        synchronized (pendingResponses) {
            pendingResponses.clear();
        }
    }

    public void onMessage(Message m, AckToken token) {
    }

    // Inherit doc from VisitorDataHandler
    public void onDocuments(DocumentList docs, AckToken token) {
        synchronized (pendingResponses) {
            pendingResponses.add(new DocumentListVisitorResponse(docs, token));
            pendingResponses.notifyAll();
        }
    }

    // Inherit doc from VisitorDataHandler
    public VisitorResponse getNext() {
        synchronized (pendingResponses) {
            return (pendingResponses.isEmpty()
                        ? null : pendingResponses.removeFirst());
        }
    }

    // Inherit doc from VisitorDataHandler
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
