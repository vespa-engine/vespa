// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.document.Document;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DummySessionFactory implements SessionFactory {

    public interface ReplyFactory {
        Reply createReply(Message m);
    }

    public final List<Message> messages;
    private boolean autoReply;
    private OutputStream output = null;

    private DummySessionFactory(boolean autoReply) {
        this.autoReply = autoReply;
        messages = new ArrayList<>();
    }

    public static DummySessionFactory createWithAutoReply() {
        return new DummySessionFactory(true);
    }

    public DummySessionFactory(OutputStream out) {
        messages = null;
        autoReply = true;
        output = out;
    }

    private void add(Message m) {
        if (messages != null) {
            messages.add(m);
        }
    }

    @Override
    public SendSession createSendSession(ReplyHandler r) {
        if (output != null) {
            return new DumpDocuments(output, r, this);
        }
        if (autoReply) {
            return new AutoReplySession(r, null, null, this);
        }
        return new DummySession(r, this);
    }

    private class MyContext {
        MyContext(ReplyHandler handler, Object ctxt) {
            this.handler = handler;
            this.oldContext = ctxt;
        }

        ReplyHandler handler;
        Object oldContext;
    }

    private class AutoReplySession extends SendSession {

        ReplyHandler handler;
        ReplyFactory replyFactory;
        Error e;
        DummySessionFactory owner;

        AutoReplySession(ReplyHandler handler, ReplyFactory replyFactory,
                                Error e, DummySessionFactory owner) {
            this.handler = handler;
            this.replyFactory = replyFactory;
            this.e = e;
            this.owner = owner;
        }

        protected void handleMessage(Message m) {

        }

        @Override
        protected Result onSend(Message m, boolean blockIfQueueFull) {
            owner.add(m);
            handleMessage(m);
            Reply r;
            if (replyFactory == null) {
                r = new EmptyReply();
            } else {
                r = replyFactory.createReply(m);
            }

            m.setTimeReceivedNow();
            r.setMessage(m);
            r.setContext(m.getContext());

            if (e != null) {
                r.addError(e);
            }
            handler.handleReply(r);
            return Result.ACCEPTED;
        }

        @Override
        public void close() {
        }

    }

    private class DumpDocuments extends AutoReplySession {
        final OutputStream out;
        DumpDocuments(OutputStream out, ReplyHandler r, DummySessionFactory factory) {
            super(r, null, null, factory);
            this.out = out;
        }
        protected void handleMessage(Message m) {
            if (m instanceof PutDocumentMessage) {
                PutDocumentMessage p = (PutDocumentMessage) m;
                Document d = p.getDocumentPut().getDocument();
                d.serialize(out);
            }
        }
    }

    private class DummySession extends SendSession {
        ReplyHandler handler;
        DummySessionFactory owner;

        DummySession(ReplyHandler handler, DummySessionFactory owner) {
            this.handler = handler;
            this.owner = owner;
        }

        @Override
        protected Result onSend(Message m, boolean blockIfQueueFull) {
            m.setContext(new MyContext(handler, m.getContext()));
            owner.add(m);
            return Result.ACCEPTED;
        }

        @Override
        public void close() {
        }
    }

}
