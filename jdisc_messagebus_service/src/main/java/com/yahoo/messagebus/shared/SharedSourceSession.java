// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;

import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class SharedSourceSession extends AbstractResource implements ClientSession, ReplyHandler {

    private static final Logger log = Logger.getLogger(SharedSourceSession.class.getName());
    private final SharedMessageBus mbus;
    private final SourceSession session;
    private final ResourceReference mbusReference;

    public SharedSourceSession(SharedMessageBus mbus, SourceSessionParams params) {
        if (params.getReplyHandler() != null) {
            throw new IllegalArgumentException("Reply handler must be null.");
        }
        this.mbus = mbus;
        this.session = mbus.messageBus().createSourceSession(params.setReplyHandler(this));
        this.mbusReference = mbus.refer();
    }

    public SourceSession session() {
        return session;
    }

    @Override
    public Result sendMessage(Message msg) {
        return session.send(msg);
    }

    public Result sendMessageBlocking(Message msg) throws InterruptedException {
        return session.sendBlocking(msg);
    }

    @Override
    public void handleReply(Reply reply) {
        reply.popHandler().handleReply(reply);
    }

    @Override
    protected void destroy() {
        log.log(LogLevel.DEBUG, "Destroying shared source session.");
        session.close();
        mbusReference.close();
    }

}
