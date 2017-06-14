// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.documentapi.*;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Trace;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub to test visitors.
 */
public class DummyVisitorSession implements VisitorSession {

    final VisitorParameters parameters;
    final DocumentType documentType;
    final List<Message> autoReplyMessages = new ArrayList<>();

    DummyVisitorSession(VisitorParameters p, DocumentType documentType) {
        parameters = p;
        this.documentType = documentType;
        p.getLocalDataHandler().setSession(this);
        addDefaultReplyMessages();
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public ProgressToken getProgress() {
        return new ProgressToken(12);
    }

    @Override
    public Trace getTrace() {
        return null;
    }

    public void addDocumentReply(String docId) {
        Document replyDoc = new Document(documentType, docId);
        autoReplyMessages.add(new PutDocumentMessage(new DocumentPut(replyDoc)));
    }

    public void addRemoveReply(String docId) {
        autoReplyMessages.add(new RemoveDocumentMessage(new DocumentId(docId)));
    }

    public void addDefaultReplyMessages() {
        addDocumentReply("userdoc:foo:1234:bar");
        if (parameters.visitRemoves()) {
            addRemoveReply("userdoc:foo:1234:removed");
        }
    }

    public void clearAutoReplyMessages() {
        autoReplyMessages.clear();
    }

    @Override
    public boolean waitUntilDone(long l) throws InterruptedException {
        for (Message msg : autoReplyMessages) {
            parameters.getLocalDataHandler().onMessage(msg, new AckToken(this));
        }
        return true;
    }

    @Override
    public void ack(AckToken ackToken) {
    }

    @Override
    public void abort() {
    }

    @Override
    public VisitorResponse getNext() {
        return null;
    }

    @Override
    public VisitorResponse getNext(int i) throws InterruptedException {
        return null;
    }

    @Override
    public void destroy() {
    }

}
