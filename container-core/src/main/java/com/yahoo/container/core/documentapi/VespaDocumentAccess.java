// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Wraps a lazily initialised {@link DocumentAccess}. Lazy to allow it to always be set up.
 * Inject this class directly (instead of DocumentAccess) for use in internal code.
 *
 * @author jonmv
 */
public class VespaDocumentAccess extends DocumentAccess {

    private final MessageBusParams parameters;

    private final AtomicReference<DocumentAccess> delegate = new AtomicReference<>();
    private boolean shutDown = false;

    VespaDocumentAccess(DocumentmanagerConfig documentmanagerConfig,
                        LoadTypeConfig loadTypeConfig,
                        String slobroksConfigId,
                        MessagebusConfig messagebusConfig,
                        DocumentProtocolPoliciesConfig policiesConfig,
                        DistributionConfig distributionConfig) {
        super(new DocumentAccessParams().setDocumentmanagerConfig(documentmanagerConfig));
        this.parameters = new MessageBusParams(new LoadTypeSet(loadTypeConfig))
                .setDocumentProtocolPoliciesConfig(policiesConfig, distributionConfig);
        this.parameters.setDocumentmanagerConfig(documentmanagerConfig);
        this.parameters.getRPCNetworkParams().setSlobrokConfigId(slobroksConfigId);
        this.parameters.getMessageBusParams().setMessageBusConfig(messagebusConfig);
    }

    public DocumentAccess delegate() {
        DocumentAccess access = delegate.getAcquire();
        return access != null ? access : delegate.updateAndGet(value -> {
            if (value != null)
                return value;

            if (shutDown)
                throw new IllegalStateException("This document access has been shut down");

            return new MessageBusDocumentAccess(parameters);
        });
    }

    @Override
    public void shutdown() {
        delegate.updateAndGet(access -> {
            super.shutdown();
            shutDown = true;
            if (access != null)
                access.shutdown();

            return null;
        });
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
