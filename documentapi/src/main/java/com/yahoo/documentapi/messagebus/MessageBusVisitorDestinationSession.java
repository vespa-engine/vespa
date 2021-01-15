// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorDestinationSession;
import com.yahoo.documentapi.VisitorResponse;
import com.yahoo.documentapi.messagebus.protocol.*;
import java.util.logging.Level;
import com.yahoo.messagebus.*;

import java.util.logging.Logger;

/**
 * A visitor destination session for receiving data from a visitor using a
 * messagebus destination session. The default behaviour of the visitor session
 * is to control visiting and receive the data. As an alternative, you may set
 * up one or more visitor destination sessions and tell the visitor to send
 * data to the remote destination(s). This is convenient if you want to receive
 * data decoupled from controlling the visitor, but also to avoid a single data
 * destination becoming a bottleneck.
 * <p>
 * Create the visitor destination session by calling the
 * <code>MessageBusDocumentAccess.createVisitorDestinationSession</code>
 * method. The visitor must be started by calling the
 * <code>MessageBusDocumentAccess.createVisitorSession</code> method and
 * progress tracked through the resulting visitor session.
 *
 * @author Thomas Gundersen
 */
public class MessageBusVisitorDestinationSession implements VisitorDestinationSession, MessageHandler
{
    private static final Logger log = Logger.getLogger(MessageBusVisitorDestinationSession.class.getName());

    private DestinationSession session;
    private VisitorDestinationParameters params;

    /**
     * Creates a message bus visitor destination session.
     *
     * @param params the parameters for the visitor destination session
     * @param bus the message bus to use
     */
    public MessageBusVisitorDestinationSession(VisitorDestinationParameters params, MessageBus bus) {
        this.params = params;
        session = bus.createDestinationSession(params.getSessionName(), true, this);
        params.getDataHandler().setSession(this);
    }

    public void handleMessage(Message message) {
        Reply reply = ((DocumentMessage)message).createReply();
        message.swapState(reply);

        params.getDataHandler().onMessage(message, new AckToken(reply));
    }

    public void ack(AckToken token) {
        try {
            log.log(Level.FINE, "Sending ack " + token.ackObject);
            session.reply((Reply) token.ackObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        session.destroy();
        session = null;
    }

    public void abort() {
        destroy();
    }

    public VisitorResponse getNext() {
        return params.getDataHandler().getNext();
    }

    public VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException {
        return params.getDataHandler().getNext(timeoutMilliseconds);
    }

}
