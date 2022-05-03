// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.documentapi;

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
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.yolean.concurrent.Memoized;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a lazily initialised {@link DocumentAccess}. Lazy to allow it to always be set up.
 * Inject this class directly (instead of DocumentAccess) for use in internal code.
 *
 * @author jonmv
 */
public class VespaDocumentAccess extends DocumentAccess {

    private static final Logger log = Logger.getLogger(VespaDocumentAccess.class.getName());

    private final MessageBusParams parameters;

    private final Memoized<DocumentAccess, RuntimeException> delegate;

    VespaDocumentAccess(DocumentmanagerConfig documentmanagerConfig,
                        String slobroksConfigId,
                        MessagebusConfig messagebusConfig,
                        DocumentProtocolPoliciesConfig policiesConfig,
                        DistributionConfig distributionConfig) {
        super(new DocumentAccessParams().setDocumentmanagerConfig(documentmanagerConfig));
        this.parameters = new MessageBusParams()
                .setDocumentProtocolPoliciesConfig(policiesConfig, distributionConfig);
        this.parameters.setDocumentmanagerConfig(documentmanagerConfig);
        this.parameters.getRPCNetworkParams().setSlobrokConfigId(slobroksConfigId);
        this.parameters.getMessageBusParams().setMessageBusConfig(messagebusConfig);
        this.delegate = new Memoized<>(() -> new MessageBusDocumentAccess(parameters), DocumentAccess::shutdown);
    }

    public DocumentAccess delegate() {
        return delegate.get();
    }

    @Override
    public void shutdown() {
        log.log(Level.WARNING, "This injected document access should only be shut down by the container", new IllegalStateException());
    }

    void protectedShutdown() {
        delegate.close();
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
