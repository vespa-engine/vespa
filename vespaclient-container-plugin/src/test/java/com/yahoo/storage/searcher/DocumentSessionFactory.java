// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.feedapi.DummySessionFactory;
import com.yahoo.feedapi.SendSession;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Used to automatically reply with a GetDocumentReply for every received GetDocumentMessage
 */
public class DocumentSessionFactory extends DummySessionFactory {

    private DocumentType docType;
    private Error error;
    // Reply instances are shared between the two collections.
    List<GetDocumentReply> autoReplies;
    Map<DocumentId, GetDocumentReply> autoReplyLookup = new HashMap<>();
    boolean autoReply = true;
    boolean nullReply = false;
    private int sessionsCreated = 0;

    private class DocumentReplySession extends SendSession {

        ReplyHandler handler;
        Error e;
        DummySessionFactory owner;

        public DocumentReplySession(ReplyHandler handler, Error e, DummySessionFactory owner) {
            this.handler = handler;
            this.e = e;
            this.owner = owner;
        }

        protected Result onSend(Message m, boolean blockIfQueueFull) throws InterruptedException {
            if (!(m instanceof GetDocumentMessage)) {
                throw new IllegalArgumentException("Expected GetDocumentMessage");
            }
            GetDocumentMessage gm = (GetDocumentMessage)m;
            owner.messages.add(m);
            if (autoReply) {
                Document replyDoc;
                if (!nullReply) {
                    replyDoc = new Document(docType, gm.getDocumentId());
                } else {
                    replyDoc = null;
                }
                Reply r = new GetDocumentReply(replyDoc);
                r.setMessage(m);
                r.setContext(m.getContext());
                if (e != null) {
                    r.addError(e);
                }
                handler.handleReply(r);
            } else if (owner.messages.size() == autoReplies.size()) {
                // Pair up all replies with their messages
                for (Message msg : owner.messages) {
                    GetDocumentReply reply = autoReplyLookup.get(((GetDocumentMessage)msg).getDocumentId());
                    reply.setMessage(msg);
                    reply.setContext(msg.getContext());
                    if (e != null) {
                        reply.addError(e);
                    }
                }
                // Now send them in the correct order. Instances are shared, so source
                // messages and contexts are properly set
                for (Reply reply : autoReplies) {
                    handler.handleReply(reply);
                }
            }

            return Result.ACCEPTED;
        }

        public void close() {
        }
    }

    public DocumentSessionFactory(DocumentType docType) {
        this.docType = docType;
        this.error = null;
    }

    public DocumentSessionFactory(DocumentType docType, Error error, boolean autoReply, GetDocumentReply... autoReplies) {
        this.docType = docType;
        this.error = error;
        this.autoReplies = Arrays.asList(autoReplies);
        for (GetDocumentReply reply : autoReplies) {
            this.autoReplyLookup.put(reply.getDocument().getId(), reply);
        }
        this.autoReply = autoReply;
    }

    public boolean isNullReply() {
        return nullReply;
    }

    public void setNullReply(boolean nullReply) {
        this.nullReply = nullReply;
    }

    public int getSessionsCreated() {
        return sessionsCreated;
    }

    public SendSession createSendSession(ReplyHandler r, Metric metric) {
        ++sessionsCreated;
        return new DocumentReplySession(r, error, this);
    }

    @Override
    public VisitorSession createVisitorSession(VisitorParameters p) {
        return new DummyVisitorSession(p, docType);
    }

}
