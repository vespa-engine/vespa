// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.Message;
import java.util.ArrayList;
import java.util.List;

/** Simplifies sending messages belonging to a single result callback. */
public class SingleSender implements SimpleFeedAccess {

    private final SharedSender.ResultCallback owner;
    private final SharedSender sender;
    private final List<MessageProcessor> messageProcessors = new ArrayList<>();

    public SingleSender(SharedSender.ResultCallback owner, SharedSender sender) {
        this.owner = owner;
        this.sender = sender;
    }

    @Override
    public void put(Document doc) {
        send(new PutDocumentMessage(new DocumentPut(doc)));
    }

    @Override
    public void remove(DocumentId docId) {
        send(new RemoveDocumentMessage(docId));
    }

    @Override
    public void update(DocumentUpdate update) {
        send(new UpdateDocumentMessage(update));
    }

    @Override
    public void put(Document doc, TestAndSetCondition condition) {
        PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(doc));
        message.setCondition(condition);
        send(message);
    }

    @Override
    public void remove(DocumentId docId, TestAndSetCondition condition) {
        RemoveDocumentMessage message = new RemoveDocumentMessage(docId);
        message.setCondition(condition);
        send(message);
    }

    @Override
    public void update(DocumentUpdate update, TestAndSetCondition condition) {
        UpdateDocumentMessage message = new UpdateDocumentMessage(update);
        message.setCondition(condition);
        send(message);
    }

    @Override
    public boolean isAborted() {
        return owner.isAborted();
    }

    public void addMessageProcessor(MessageProcessor processor) {
        messageProcessors.add(processor);
    }

    // Runs all message processors on the message and returns it.
    private Message processMessage(Message m) {
        for (MessageProcessor processor : messageProcessors) {
            processor.process(m);
        }
        return m;
    }

    /**
     * Sends the given message.
     *
     * @param m          The message to send
     */
    public void send(Message m) {
        sender.send(processMessage(m), owner, true);
    }

    public void done() {
        // empty
    }

    public boolean waitForPending(long timeoutMs) {
        return sender.waitForPending(owner, timeoutMs);
    }

    @Override
    public void close() { }
}
