// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.document.Document;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.jdisc.Metric;
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
    private boolean autoReply = false;
    private ReplyFactory autoReplyFactory = null;
    private Error autoError;
    private int sessionsCreated = 0;
    OutputStream output = null;

    protected DummySessionFactory() {
        messages = new ArrayList<>();
    }

    public static DummySessionFactory createDefault() {
        return new DummySessionFactory();
    }

    protected DummySessionFactory(boolean autoReply) {
        this.autoReply = autoReply;
        messages = new ArrayList<>();
    }

    protected DummySessionFactory(ReplyFactory autoReplyFactory) {
        this.autoReply = true;
        this.autoReplyFactory = autoReplyFactory;
        messages = new ArrayList<>();
    }

    public static DummySessionFactory createWithAutoReplyFactory(ReplyFactory autoReplyFactory) {
        return new DummySessionFactory(autoReplyFactory);
    }

    protected DummySessionFactory(Error e) {
        autoReply = true;
        this.autoError = e;
        messages = new ArrayList<>();
    }

    public static DummySessionFactory createWithErrorAutoReply(Error e) {
        return new DummySessionFactory(e);
    }

    public static DummySessionFactory createWithAutoReply() {
        return new DummySessionFactory(true);
    }

    public DummySessionFactory(Error e, OutputStream out) {
        messages = null;
        autoReply = true;
        output = out;
    }

    public int sessionsCreated() {
        return sessionsCreated;
    }
    void add(Message m) {
        if (messages != null) {
            messages.add(m);
        }

    }

    @Override
    public SendSession createSendSession(ReplyHandler r, Metric metric) {
        ++sessionsCreated;

        if (output != null) {
            return new DumpDocuments(output, r, this);
        }
        if (autoReply) {
            return new AutoReplySession(r, autoReplyFactory, autoError, this);
        }
        return new DummySession(r, this);
    }

    @Override
    public VisitorSession createVisitorSession(VisitorParameters p) {
        return null;
    }

    public void sendReply(Message m, Error error) {
        MyContext ctxt = (MyContext) m.getContext();

        Reply r = new EmptyReply();
        r.setMessage(m);
        r.setContext(ctxt.oldContext);

        if (error != null) {
            r.addError(error);
        }

        ctxt.handler.handleReply(r);
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

        public AutoReplySession(ReplyHandler handler, ReplyFactory replyFactory,
                                Error e, DummySessionFactory owner) {
            this.handler = handler;
            this.replyFactory = replyFactory;
            this.e = e;
            this.owner = owner;
        }

        protected void handleMessage(Message m) {

        }

        @Override
        protected Result onSend(Message m, boolean blockIfQueueFull) throws InterruptedException {
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
        public DumpDocuments(OutputStream out, ReplyHandler r, DummySessionFactory factory) {
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

        public DummySession(ReplyHandler handler, DummySessionFactory owner) {
            this.handler = handler;
            this.owner = owner;
        }

        @Override
        protected Result onSend(Message m, boolean blockIfQueueFull) throws InterruptedException {
            m.setContext(new MyContext(handler, m.getContext()));
            owner.add(m);
            return Result.ACCEPTED;
        }

        @Override
        public void close() {
        }
    }

}
