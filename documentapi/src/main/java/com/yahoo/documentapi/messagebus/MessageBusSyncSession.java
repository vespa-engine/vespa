// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.DocumentAccessException;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;

/**
 * An implementation of the SyncSession interface running over message bus.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class MessageBusSyncSession implements MessageBusSession, SyncSession, ReplyHandler {

    private MessageBusAsyncSession session;

    /**
     * Creates a new sync session running on message bus logic.
     *
     * @param syncParams Common syncsession parameters, not used.
     * @param bus        The message bus on which to run.
     * @param mbusParams Parameters concerning message bus configuration.
     */
    MessageBusSyncSession(SyncParameters syncParams, MessageBus bus, MessageBusParams mbusParams) {
        session = new MessageBusAsyncSession(new AsyncParameters(), bus, mbusParams, this);
    }

    @Override
    public void handleReply(Reply reply) {
        if (reply.getContext() instanceof RequestMonitor) {
            ((RequestMonitor)reply.getContext()).replied(reply);
        } else {
            ReplyHandler handler = reply.getCallStack().pop(reply);
            handler.handleReply(reply); // not there yet
        }
    }

    @Override
    public Response getNext() {
        throw new UnsupportedOperationException("Queue not supported.");
    }

    @Override
    public Response getNext(int timeout) {
        throw new UnsupportedOperationException("Queue not supported.");
    }

    @Override
    public void destroy() {
        session.destroy();
    }

    /**
     * Perform a synchronous sending of a message. This method block until the message is successfuly sent and a
     * corresponding reply has been received.
     *
     * @param msg The message to send.
     * @return The reply received.
     */
    public Reply syncSend(Message msg) {
        try {
            RequestMonitor monitor = new RequestMonitor();
            msg.setContext(monitor);
            msg.pushHandler(this); // store monitor
            Result result = null;
            while (result == null || result.getType() == Result.ResultType.TRANSIENT_ERROR) {
                result = session.send(msg);
                if (result != null && result.isSuccess()) {
                    break;
                }
                Thread.sleep(100);
            }
            if (!result.isSuccess()) {
                throw new DocumentAccessException(result.getError().toString());
            }
            return monitor.waitForReply();
        } catch (InterruptedException e) {
            throw new DocumentAccessException(e);
        }
    }

    @Override
    public void put(DocumentPut documentPut) {
        put(documentPut, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public void put(DocumentPut documentPut, DocumentProtocol.Priority priority) {
        PutDocumentMessage msg = new PutDocumentMessage(documentPut);
        msg.setPriority(priority);
        syncSendPutDocumentMessage(msg);
    }

    @Override
    public Document get(DocumentId id) {
        return get(id, "[all]", DocumentProtocol.Priority.NORMAL_1);
    }

    @Override
    public Document get(DocumentId id, String fieldSet, DocumentProtocol.Priority pri) {
        GetDocumentMessage msg = new GetDocumentMessage(id, fieldSet);
        msg.setPriority(pri);

        Reply reply = syncSend(msg);
        if (reply.hasErrors()) {
            throw new DocumentAccessException(MessageBusAsyncSession.getErrorMessage(reply));
        }
        if (reply.getType() != DocumentProtocol.REPLY_GETDOCUMENT) {
            throw new DocumentAccessException("Received unknown response: " + reply);
        }
        GetDocumentReply docReply = ((GetDocumentReply)reply);
        Document doc = docReply.getDocument();
        if (doc != null) {
            doc.setLastModified(docReply.getLastModified());
        }
        return doc;
    }

    @Override
    public boolean remove(DocumentRemove documentRemove) {
        RemoveDocumentMessage msg = new RemoveDocumentMessage(documentRemove.getId());
        msg.setCondition(documentRemove.getCondition());
        return remove(msg);
    }

    @Override
    public boolean remove(DocumentRemove documentRemove, DocumentProtocol.Priority pri) {
        RemoveDocumentMessage msg = new RemoveDocumentMessage(documentRemove.getId());
        msg.setPriority(pri);
        msg.setCondition(documentRemove.getCondition());
        return remove(msg);
    }

    private boolean remove(RemoveDocumentMessage msg) {
        Reply reply = syncSend(msg);
        if (reply.hasErrors()) {
            throw new DocumentAccessException(MessageBusAsyncSession.getErrorMessage(reply));
        }
        if (reply.getType() != DocumentProtocol.REPLY_REMOVEDOCUMENT) {
            throw new DocumentAccessException("Received unknown response: " + reply);
        }
        return ((RemoveDocumentReply)reply).wasFound();
    }

    @Override
    public boolean update(DocumentUpdate update) {
        return update(update, DocumentProtocol.Priority.NORMAL_2);
    }

    @Override
    public boolean update(DocumentUpdate update, DocumentProtocol.Priority pri) {
        UpdateDocumentMessage msg = new UpdateDocumentMessage(update);
        msg.setPriority(pri);
        Reply reply = syncSend(msg);
        if (reply.hasErrors()) {
            throw new DocumentAccessException(MessageBusAsyncSession.getErrorMessage(reply),
                    MessageBusAsyncSession.getErrorCodes(reply));
        }
        if (reply.getType() != DocumentProtocol.REPLY_UPDATEDOCUMENT) {
            throw new DocumentAccessException("Received unknown response: " + reply);
        }
        return ((UpdateDocumentReply)reply).wasFound();
    }

    @Override
    public String getRoute() {
        return session.getRoute();
    }

    @Override
    public void setRoute(String route) {
        session.setRoute(route);
    }

    @Override
    public int getTraceLevel() {
        return session.getTraceLevel();
    }

    @Override
    public void setTraceLevel(int traceLevel) {
        session.setTraceLevel(traceLevel);
    }

    /**
     * This class implements a monitor for waiting for a reply to arrive.
     */
    static class RequestMonitor {
        private Reply reply = null;

        synchronized Reply waitForReply() throws InterruptedException {
            while (reply == null) {
                wait();
            }
            return reply;
        }

        synchronized void replied(Reply reply) {
            this.reply = reply;
            notify();
        }
    }

    private void syncSendPutDocumentMessage(PutDocumentMessage putDocumentMessage) {
        Reply reply = syncSend(putDocumentMessage);
        if (reply.hasErrors()) {
            throw new DocumentAccessException(MessageBusAsyncSession.getErrorMessage(reply),
                    MessageBusAsyncSession.getErrorCodes(reply));
        }
    }
}
