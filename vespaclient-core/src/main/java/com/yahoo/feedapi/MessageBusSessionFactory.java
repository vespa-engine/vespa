// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;

import java.util.Collections;

public class MessageBusSessionFactory implements SessionFactory {

    private final MessageBusDocumentAccess access;
    private final MessagePropertyProcessor processor;

    private interface Metrics {
        String NUM_OPERATIONS = "num_operations";
        String NUM_PUTS = "num_puts";
        String NUM_REMOVES = "num_removes";
        String NUM_UPDATES = "num_updates";
    }

    public MessageBusSessionFactory(MessagePropertyProcessor processor) {
        this(processor, null, null);
    }
    
    public MessageBusSessionFactory(MessagePropertyProcessor processor, 
                                    DocumentmanagerConfig documentmanagerConfig,
                                    SlobroksConfig slobroksConfig) {
        this.processor = processor;
        MessageBusParams params = new MessageBusParams(processor.getLoadTypes());
        params.setTraceLevel(processor.getFeederOptions().getTraceLevel());
        RPCNetworkParams rpcNetworkParams = processor.getFeederOptions().getNetworkParams();
        if (slobroksConfig != null) // not set: will subscribe
            rpcNetworkParams.setSlobroksConfig(slobroksConfig);
        params.setRPCNetworkParams(rpcNetworkParams);
        params.setDocumentManagerConfigId("client");
        if (documentmanagerConfig != null) // not set: will subscribe
            params.setDocumentmanagerConfig(documentmanagerConfig);
        access = new MessageBusDocumentAccess(params);
    }

    public MessageBusDocumentAccess getAccess() {
        return access;
    }

    @Override
    public synchronized SendSession createSendSession(ReplyHandler handler, Metric metric) {
        return new SourceSessionWrapper(
                access.getMessageBus().createSourceSession(handler, processor.getFeederOptions().toSourceSessionParams()),
                metric);
    }

    public void shutDown() {
        access.shutdown();
    }

    private class SourceSessionWrapper extends SendSession {

        private final SourceSession session;
        private final Metric metric;
        private final Metric.Context context;

        private SourceSessionWrapper(SourceSession session, Metric metric) {
            this.session = session;
            this.metric = metric;
            this.context = metric.createContext(Collections.<String, String>emptyMap());
        }

        @Override
        protected com.yahoo.messagebus.Result onSend(Message m, boolean blockIfQueueFull) throws InterruptedException {
            updateCounters(m);
            if (blockIfQueueFull) {
                return session.sendBlocking(m);
            } else {
                return session.send(m);
            }
        }

        private void updateCounters(Message m) {
            metric.add(Metrics.NUM_OPERATIONS, 1, context);

            if (m instanceof PutDocumentMessage) {
                metric.add(Metrics.NUM_PUTS, 1, context);
            } else if (m instanceof RemoveDocumentMessage) {
                metric.add(Metrics.NUM_REMOVES, 1, context);
            } else if (m instanceof UpdateDocumentMessage) {
                metric.add(Metrics.NUM_UPDATES, 1, context);
            }
        }

        @Override
        public void close() {
            session.close();
        }
    }

}
