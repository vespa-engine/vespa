// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.DocumentOperation;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.Message;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * A visitor data handler that queues up documents in visitor responses and
 * implements the <code>getNext</code> methods, thus implementing the polling
 * API defined in VisitorDataHandler.
 * <p>
 * Visitor responses should be polled for with the
 * <code>getNext</code> methods and need to be acked when processed for
 * visiting not to halt. The class is thread safe.
 *
 * @author Håkon Humberset
 * @author vekterli
 */
public class VisitorDataQueue extends VisitorDataHandler {

    private final BlockingQueue<VisitorResponse> pendingResponses = new LinkedBlockingQueue<>();

    /** Creates a new visitor data queue. */
    public VisitorDataQueue() {
    }

    // Inherit doc from VisitorDataHandler
    @Override
    public void reset() {
        super.reset();
        pendingResponses.clear();
    }

    private void appendSingleOpToPendingList(final DocumentOperation op, final AckToken token) {
        final DocumentOpVisitorResponse response = new DocumentOpVisitorResponse(op, token);
        pendingResponses.add(response);
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

    // Inherit doc from VisitorDataHandler
    @Override
    public VisitorResponse getNext() {
        return pendingResponses.poll();
    }

    // Inherit doc from VisitorDataHandler
    @Override
    public VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException {
        return pendingResponses.poll(timeoutMilliseconds, TimeUnit.MILLISECONDS);
    }

}
