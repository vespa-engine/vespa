// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.messagebus.metrics.RouteMetricSet;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.routing.Resender;
import com.yahoo.messagebus.routing.RoutingNode;
import com.yahoo.log.LogLevel;

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
    private long sendTime = 0;

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
        sendTime = SystemTimer.INSTANCE.milliTime();
    }

    public void handleMessage(Message msg) {
        Trace trace = msg.getTrace();
        if (trace.getLevel() == 0) {
            if (log.isLoggable(LogLevel.SPAM)) {
                trace.setLevel(9);
                logTrace = true;
            } else if (log.isLoggable(LogLevel.DEBUG)) {
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
                if (reply.hasErrors()) {
                    log.log(LogLevel.DEBUG, "Trace for reply with error(s):\n" + reply.getTrace());
                } else if (log.isLoggable(LogLevel.SPAM)) {
                    log.log(LogLevel.SPAM, "Trace for reply:\n" + reply.getTrace());
                }
                Trace empty = new Trace();
                trace.swap(empty);
            } else if (trace.getLevel() > 0) {
                trace.getRoot().addChild(reply.getTrace().getRoot());
                trace.getRoot().normalize();
            }
            reply.swapState(msg);
            reply.setMessage(msg);

            if (msg.getRoute() != null) {
                RouteMetricSet metrics = mbus.getMetrics().getRouteMetrics(msg.getRoute());
                for (int i = 0; i < reply.getNumErrors(); i++) {
                    metrics.addFailure(reply.getError(i));
                }
                if (reply.getNumErrors() == 0) {
                    metrics.latency.addValue(msg.getTimeReceived() - sendTime);
                }
            }

            ReplyHandler handler = reply.popHandler();
            handler.handleReply(reply);
        }
    }
}
