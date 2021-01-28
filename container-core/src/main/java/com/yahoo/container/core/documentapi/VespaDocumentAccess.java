// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.documentapi;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.SubscriptionParameters;
import com.yahoo.documentapi.SubscriptionSession;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorDestinationSession;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;

/**
 * Wraps a lazily initialised MessageBusDocumentAccess. Lazy to allow it to always be set up.
 * Inject this class directly (instead of DocumentAccess) for use in internal code.
 *
 * @author jonmv
 */
public class VespaDocumentAccess extends DocumentAccess {

    private final MessageBusParams parameters;
    private final Object monitor = new Object();

    private DocumentAccess delegate = null;
    private boolean shutDown = false;

    VespaDocumentAccess(DocumentmanagerConfig documentmanagerConfig,
                        LoadTypeConfig loadTypeConfig,
                        SlobroksConfig slobroksConfig,
                        MessagebusConfig messagebusConfig,
                        DocumentProtocolPoliciesConfig policiesConfig,
                        DistributionConfig distributionConfig) {
        super(new DocumentAccessParams().setDocumentmanagerConfig(documentmanagerConfig));
        this.parameters = new MessageBusParams(new LoadTypeSet(loadTypeConfig))
                .setDocumentProtocolPoliciesConfig(policiesConfig, distributionConfig);
        this.parameters.setDocumentmanagerConfig(documentmanagerConfig);
        this.parameters.getRPCNetworkParams().setSlobroksConfig(slobroksConfig);
        this.parameters.getMessageBusParams().setMessageBusConfig(messagebusConfig);
    }

    private DocumentAccess delegate() {
        synchronized (monitor) {
            if (delegate == null) {
                if (shutDown)
                    throw new IllegalStateException("This document access has been shut down");

                delegate = new MessageBusDocumentAccess(parameters);
            }
            return delegate;
        }
    }

    @Override
    public void shutdown() {
        synchronized (monitor) {
            super.shutdown();
            shutDown = true;
            if (delegate != null)
                delegate.shutdown();
        }
    }

    @Override
    public SyncSession createSyncSession(SyncParameters parameters) {
        return delegate().createSyncSession(parameters);
    }

    @Override
    public AsyncSession createAsyncSession(AsyncParameters parameters) {
        return delegate().createAsyncSession(parameters);
    }

    @Override
    public VisitorSession createVisitorSession(VisitorParameters parameters) throws ParseException {
        return delegate().createVisitorSession(parameters);
    }

    @Override
    public VisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters parameters) {
        return delegate().createVisitorDestinationSession(parameters);
    }

    @Override
    public SubscriptionSession createSubscription(SubscriptionParameters parameters) {
        return delegate().createSubscription(parameters);
    }

    @Override
    public SubscriptionSession openSubscription(SubscriptionParameters parameters) {
        return delegate().openSubscription(parameters);
    }

}
