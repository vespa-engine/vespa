// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;

public class MessageBusSessionFactory implements SessionFactory {

    private final MessageBusDocumentAccess access;
    private final MessagePropertyProcessor processor;

    public MessageBusSessionFactory(MessagePropertyProcessor processor) {
        this(processor, null, null);
    }

    private MessageBusSessionFactory(MessagePropertyProcessor processor,
                                    DocumentmanagerConfig documentmanagerConfig,
                                    SlobroksConfig slobroksConfig) {
        this.processor = processor;
        MessageBusParams params = new MessageBusParams();
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
    public synchronized SendSession createSendSession(ReplyHandler handler) {
        return new SourceSessionWrapper(
                access.getMessageBus().createSourceSession(handler, processor.getFeederOptions().toSourceSessionParams()));
    }

    public void shutDown() {
        access.shutdown();
    }

    private class SourceSessionWrapper extends SendSession {

        private final SourceSession session;

        private SourceSessionWrapper(SourceSession session) {
            this.session = session;
        }

        @Override
        protected com.yahoo.messagebus.Result onSend(Message m, boolean blockIfQueueFull) throws InterruptedException {
            if (blockIfQueueFull) {
                return session.sendBlocking(m);
            } else {
                return session.send(m);
            }
        }

        @Override
        public void close() {
            session.close();
        }
    }

}
