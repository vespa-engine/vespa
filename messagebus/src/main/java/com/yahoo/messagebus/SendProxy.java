// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.routing.Resender;
import com.yahoo.messagebus.routing.RoutingNode;
import java.util.logging.Level;

import java.util.logging.Logger;

/**
 * This class owns a message that is being sent by message bus. Once a reply is received, the message is attached to it
 * and returned to the application. This also implements the discard policy of {@link RoutingNode}.
 *
 * @author Simon Thoresen Hult
 */
public class SendProxy implements MessageHandler, ReplyHandler {

    private static final Logger log = Logger.getLogger(SendProxy.class.getName());
    private final MessageBus mbus;
    private final Network net;
    private final Resender resender;
    private Message msg = null;
    private boolean logTrace = false;

    /**
     * Constructs a new instance of this class to maintain sending of a single message.
     *
     * @param mbus     The message bus that owns this.
     * @param net      The network layer to transmit through.
     * @param resender The resender to use.
     */
    public SendProxy(MessageBus mbus, Network net, Resender resender) {
        this.mbus = mbus;
        this.net = net;
        this.resender = resender;
    }

    public void handleMessage(Message msg) {
        Trace trace = msg.getTrace();
        if (trace.getLevel() == 0) {
            if (log.isLoggable(Level.FINEST)) {
                trace.setLevel(9);
                logTrace = true;
            } else if (log.isLoggable(Level.FINE)) {
                trace.setLevel(6);
                logTrace = true;
            }
        }
        this.msg = msg;
        RoutingNode root = new RoutingNode(mbus, net, resender, this, msg);
        root.send();
    }

    public void handleReply(Reply reply) {
        if (reply == null) {
            msg.discard();
        } else {
            Trace trace = msg.getTrace();
            if (logTrace) {
                if (reply.hasErrors())
                    log.log(Level.FINE, () -> "Trace for reply with error(s):\n" + reply.getTrace());
                else
                    log.log(Level.FINEST, () -> "Trace for reply:\n" + reply.getTrace());
                Trace empty = new Trace();
                trace.swap(empty);
            } else if (trace.getLevel() > 0) {
                trace.getRoot().addChild(reply.getTrace().getRoot());
                trace.getRoot().normalize();
            }
            reply.swapState(msg);
            reply.setMessage(msg);

            ReplyHandler handler = reply.popHandler();
            handler.handleReply(reply);
        }
    }

}
